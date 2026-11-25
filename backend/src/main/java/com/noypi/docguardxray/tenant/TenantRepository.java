package com.noypi.docguardxray.tenant;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    
    Optional<Tenant> findByIdAndActiveTrue(UUID tenantId);

    Optional<Tenant> findBySlugAndActiveTrue(String slug);
}
