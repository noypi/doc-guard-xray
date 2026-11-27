package com.noypi.docguardxray.tenant;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.noypi.docguardxray.utils.HashUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;

    public ApiKeyAuthFilter(
            ApiKeyRepository apiKeyRepository,
            TenantRepository tenantRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override 
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();

        // SECURITY: evil traversal 
        return path.startsWith("/api/v1/public");
    }
    
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false; // Run on ASYNC dispatch as well
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String apiKey = request.getHeader("X-API-KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                log.debug("no apiKey provided");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing API key");
                return;
            }

            String apiKeyHash = HashUtils.sha256Hex(apiKey);
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHashAndActiveTrue(apiKeyHash);
            if (apiKeyOpt.isEmpty()) {
                log.warn("apiKeyHash is not found, hash:{}", apiKeyHash);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                return;
            }

            ApiKey apiKeyEntity = apiKeyOpt.get();
            Optional<Tenant> tenantOpt = tenantRepository.findByIdAndActiveTrue(apiKeyEntity.getTenantId());
            if (tenantOpt.isEmpty()) {
                log.warn("tenant is not found, id:{}", apiKeyEntity.getTenantId());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Tenant not found for API key");
                return;
            } else {
                TenantContext.setTenantInfo(
                        tenantOpt.get().getId(),
                        tenantOpt.get().getSlug(),
                        tenantOpt.get().getMonthlyTokens());

                var auth = new UsernamePasswordAuthenticationToken(tenantOpt.get().getSlug(), null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            Tenant tenant = tenantOpt.get();
            request.setAttribute("tenant", tenant);

            log.debug("tenantContext set to:{}", tenant.getId());
            filterChain.doFilter(request, response);

        } finally {
            log.debug("clearing tenantId:{}", TenantContext.getTenantId());
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
