package com.noypi.docguardxray.tenant;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_keys")
public class ApiKey {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Generated
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected ApiKey() {}
    
    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getLabel() {
        return label;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}
