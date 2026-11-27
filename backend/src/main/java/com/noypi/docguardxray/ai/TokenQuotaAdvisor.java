package com.noypi.docguardxray.ai;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.noypi.docguardxray.constant.AdvisorOrders;
import com.noypi.docguardxray.tenant.TenantContext;

import reactor.core.publisher.Flux;

@Component 
public class TokenQuotaAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenQuotaAdvisor.class);
    private final UsageLogService usageLogService;
    private static final int DEFAULT_ESTIMATED_RESERVE = 1500;
    private static final long TTL_35_DAYS_SECONDS = 3_024_000L;

    private final StringRedisTemplate redisTemplate;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> reserveTokenScript;

    public TokenQuotaAdvisor(StringRedisTemplate redisTemplate, UsageLogService usageLogService) {
        this.redisTemplate = redisTemplate;
        this.reserveTokenScript = new DefaultRedisScript<>();
        this.reserveTokenScript.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("scripts/reserve_tokens.lua")
        ));
        this.reserveTokenScript.setResultType(List.class);
        this.usageLogService = usageLogService;
    }

    @Override
    public String getName() {
        return "TokenQuotaAdvisor";
    }

    @Override
    public int getOrder() {
        return AdvisorOrders.TOKEN_QUOTA;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request,
            StreamAdvisorChain chain) {
        UUID tenantId = TenantContext.getTenantId();
        int monthlyLimit = TenantContext.getMonthlyLimit();
        String cacheKey = getCacheKey(tenantId);
        XRayState xRayState = getXRayState(request);

        log.debug("adviseStream id:{}, limit:{}", tenantId, monthlyLimit);

        var reserveSubtracted = new AtomicBoolean(false);
        var remainingAfterReserve = new AtomicInteger(reserveQuota(tenantId, monthlyLimit, DEFAULT_ESTIMATED_RESERVE));

        log.debug("adviseStream remainingAfterReserve:{}", remainingAfterReserve);

        return chain.nextStream(request)
                .doOnNext(chunk -> {
                    int usageTokens = extractTotalTokens(chunk);
                    // since we assumed a default usage, we need to update and use the actual usage
                    int delta = !reserveSubtracted.get()
                            ? usageTokens - DEFAULT_ESTIMATED_RESERVE
                            : usageTokens;
                    reserveSubtracted.set(true);

                    log.debug("onNext usageTokens:{}, delta:{}", usageTokens, delta);
                    
                    if (usageTokens != DEFAULT_ESTIMATED_RESERVE && delta != 0) {
                        this.redisTemplate.opsForValue().increment(cacheKey, delta);
                    }

                    int updated = remainingAfterReserve.addAndGet(delta);
                    xRayState.recordTokens(usageTokens, updated);
                    recordUsage(tenantId, chunk, xRayState);
                });
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        UUID tenantId = TenantContext.getTenantId();
        int monthlyLimit = TenantContext.getMonthlyLimit();
        String cacheKey = getCacheKey(tenantId);
        XRayState xRayState = getXRayState(request);

        int remainingAfterReserve = reserveQuota(tenantId, monthlyLimit, DEFAULT_ESTIMATED_RESERVE);
        ChatClientResponse response = chain.nextCall(request);

        int usageTokens = extractTotalTokens(response);
        // since we assumed a default usage, we need to update and use the actual usage
        int delta = usageTokens - DEFAULT_ESTIMATED_RESERVE;
        if (usageTokens != DEFAULT_ESTIMATED_RESERVE && delta != 0) {
            this.redisTemplate.opsForValue().increment(cacheKey, delta);
        }

        xRayState.recordTokens(usageTokens, remainingAfterReserve - delta);
        recordUsage(tenantId, response, xRayState);

        return response;
    }
    
    private int reserveQuota(UUID tenantId, int monthlyLimit, int tokensToReserve) {
        String key = getCacheKey(tenantId);
        List<?> results = redisTemplate.execute(
                this.reserveTokenScript,
                List.of(key),
                String.valueOf(monthlyLimit),
                String.valueOf(tokensToReserve),
                String.valueOf(TTL_35_DAYS_SECONDS));

        int remaining = Math.toIntExact(((Number) results.get(0)).longValue());
        boolean isNew = ((Number) results.get(1)).intValue() == 1;

        if (isNew) {
            remaining = rehydrateMonthlyQuotaFromLogs(tenantId, remaining);
        }

        if (remaining <= 0) {
            log.debug("quota exceeded, redis key={}, remaining={}", key, remaining);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Monthly token quota exceeded");
        }

        return remaining;
    }

    private int rehydrateMonthlyQuotaFromLogs(UUID tenantId, int remaining) {
        int used = this.usageLogService.sumTokensUsedByTenantThisMonth(tenantId);
        if (used == 0) {
            return remaining;
        }

        String cacheKey = getCacheKey(tenantId);
        this.redisTemplate.opsForValue().increment(cacheKey, used);

        return remaining - used;
    }
    
    private void recordUsage(UUID tenantId, ChatClientResponse response, XRayState xRayState) {
        this.usageLogService.recordUsage(
                tenantId,
                xRayState.getSource(),
                xRayState.getTokensUsed(),
                xRayState.getPiiRedactedCount(),
                xRayState.getTraceId());
    }
    
    private int extractTotalTokens(ChatClientResponse response) {
        return Optional.ofNullable(response)
                .map(ChatClientResponse::chatResponse)
                .map(ChatResponse::getMetadata)
                .map(ChatResponseMetadata::getUsage)
                .map(usage -> Optional.ofNullable(usage.getTotalTokens())
                        .filter(val -> 0 < val)
                        .orElseGet(() -> Objects.requireNonNullElse(usage.getPromptTokens(), 0)
                                + Objects.requireNonNullElse(usage.getCompletionTokens(), 0)))
                .orElse(0);
    }
    
    private String getCacheKey(UUID tenantId) {
        return "tenant:%s:tokens:%s".formatted(tenantId, YearMonth.now());
    }

    private XRayState getXRayState(ChatClientRequest request) {
        XRayState xRayState = (XRayState) request.context().get(XRayState.class.getName());
        if (xRayState == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "xray was not set");
        }

        return xRayState;
    }
}
