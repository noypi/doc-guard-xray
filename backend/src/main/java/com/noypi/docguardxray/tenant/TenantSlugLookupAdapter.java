package com.noypi.docguardxray.tenant;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.noypi.docguardxray.common.ActiveTenantLookup;

@Component 
public class TenantSlugLookupAdapter implements ActiveTenantLookup {
    
    private final TenantRepository tenantRepository;

    TenantSlugLookupAdapter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override 
    public Optional<UUID> findActiveTenantIdBySlug(String slug) {
        return this.tenantRepository.findBySlugAndActiveTrue(slug)
                    .map(Tenant::getId);
    }
}
