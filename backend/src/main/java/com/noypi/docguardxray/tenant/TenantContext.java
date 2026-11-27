package com.noypi.docguardxray.tenant;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class TenantContext {
    private static final ThreadLocal<TenantInfo> HOLDER = new ThreadLocal<>();

    public record TenantInfo(UUID tenantId, String tenantSlug, int monthlyLimit) {}
    
    public static void setTenantInfo(UUID tenantId, String tenantSlug, int monthlyLimit) {
        HOLDER.set(new TenantInfo(tenantId, tenantSlug, monthlyLimit));
    }

    public static Optional<TenantInfo> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static UUID getTenantId() {
        return require().tenantId();
    }

    public static int getMonthlyLimit() {
        return require().monthlyLimit();
    }

    public static TenantInfo require() {
        return current().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing tenant context"));
    }

    public static void clear() {
        HOLDER.remove();
    }
}
