package com.noypi.docguardxray.ai;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.noypi.docguardxray.constant.UsageSource;

public final class XRayState {

    private static final Logger log = LoggerFactory.getLogger(XRayState.class);
    private static final UsageSource DEFAULT_SOURCE = UsageSource.WEB;

    private UsageSource source = DEFAULT_SOURCE;
    private String traceId;
    private String originalPrompt = "";
    private String maskedPrompt = "";
    private int piiRedactedCount = 0;
    private List<ChatDtos.Citation> citations = List.of();
    private int tokensUsed = 0;
    private int remainingMonthlyQuota = 0;

    public XRayState(UsageSource source) {
        this.source = (source != null) ? source : DEFAULT_SOURCE;
        String mdcTrace = MDC.get("traceId");
        this.traceId = (mdcTrace != null && !mdcTrace.isBlank()) ? mdcTrace : UUID.randomUUID().toString();

        log.debug("create traceId:{}", this.traceId);
    }

    public ChatDtos.XRayTrace toTrace(long startTimeMillis) {
        long latencyMs = Math.max(0L, System.currentTimeMillis() - startTimeMillis);

        return new ChatDtos.XRayTrace(
                traceId,
                originalPrompt,
                maskedPrompt,
                piiRedactedCount,
                citations,
                tokensUsed,
                remainingMonthlyQuota,
                latencyMs);
    }

    public void recordPii(String originalPrompt, String maskedPrompt, int count) {
        this.originalPrompt = originalPrompt;
        this.maskedPrompt = maskedPrompt;
        this.piiRedactedCount = count;
    }

    public void recordCitations(List<ChatDtos.Citation> citations) {
        this.citations = (citations != null) ? citations : List.of();
    }

    public void recordTokens(int tokensUsedDelta, int remainingQuota) {
        this.tokensUsed += tokensUsedDelta;
        this.remainingMonthlyQuota = remainingQuota;
    }

    public UsageSource getSource() {
        return this.source;
    }

    public int getTokensUsed() {
        return this.tokensUsed;
    }

    public int getPiiRedactedCount() {
        return this.piiRedactedCount;
    }

    public String getTraceId() {
        return this.traceId;
    }
}
