package com.noypi.docguardxray.tenant;

import java.io.IOException;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.noypi.docguardxray.constant.FilterOrders;
import com.noypi.docguardxray.utils.HashUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order (FilterOrders.REQUEST_API)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

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
        return path.startsWith("/api/vi/public");
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String apiKey = request.getHeader("X-API-KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing API key");
                return;
            }

            String apiKeyHash = HashUtils.sha256Hex(apiKey);
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHashAndActiveTrue(apiKeyHash);
            if (apiKeyOpt.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                return;
            }

            ApiKey apiKeyEntity = apiKeyOpt.get();
            Optional<Tenant> tenantOpt = tenantRepository.findByIdAndActiveTrue(apiKeyEntity.getTenantId());
            if (tenantOpt.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Tenant not found for API key");
                return;
            } else {
                TenantContext.setTenantInfo(tenantOpt.get().getId(), tenantOpt.get().getSlug());
            }

            Tenant tenant = tenantOpt.get();
            request.setAttribute("tenant", tenant);

            filterChain.doFilter(request, response);

        } finally {
            TenantContext.clear();
        }
    }
}
