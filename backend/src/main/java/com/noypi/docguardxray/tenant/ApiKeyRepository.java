package com.noypi.docguardxray.tenant;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    
    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);

    Optional<ApiKey> findByIdAndActiveTrue(UUID id);
}
