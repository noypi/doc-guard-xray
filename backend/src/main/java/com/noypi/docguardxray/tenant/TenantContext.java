package com.noypi.docguardxray.tenant;

import java.util.Optional;
import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<TenantInfo> tenantInfoThreadLocal = new ThreadLocal<>();

    public record TenantInfo(UUID tenantId, String tenantSlug) {}
    
    public static void setTenantInfo(UUID tenantId, String tenantSlug) {
        tenantInfoThreadLocal.set(new TenantInfo(tenantId, tenantSlug));
    }

    public static Optional<TenantInfo> getTenantInfo() {
        return Optional.ofNullable(tenantInfoThreadLocal.get());
    }

    public static Optional<UUID> getTenantId() {
        return getTenantInfo().map(TenantInfo::tenantId);
    }

    public static void clear() {
        tenantInfoThreadLocal.remove();
    }
}
