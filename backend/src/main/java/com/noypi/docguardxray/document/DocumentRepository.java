package com.noypi.docguardxray.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findAllByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    Optional<Document> findByTenantIdAndContentHash(UUID tenantId, String contentHash);

    Optional<Document> findByTenantIdAndSlugAndIsPublicTrue(UUID tenantId, String docSlug);

    Page<Document> findAllByTenantId(UUID tenantId, Pageable pageable);
}

