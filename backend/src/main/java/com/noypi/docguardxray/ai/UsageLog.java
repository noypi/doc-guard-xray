package com.noypi.docguardxray.ai;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;

import com.noypi.docguardxray.constant.UsageSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table (name = "usage_logs")
public class UsageLog {

    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "tokens_used", nullable = false)
    private int tokensUsed;

    @Column(name = "pii_redacted", nullable = false)
    private int piiRedacted;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Generated 
    @Column (name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected UsageLog() {}

    public UsageLog(UUID tenantId, UsageSource source, int tokensUsed, int piiRedacted, String traceId) {
        this.tenantId = tenantId;
        this.source = source.name();
        this.tokensUsed = tokensUsed;
        this.piiRedacted = piiRedacted;
        this.traceId = traceId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UsageSource getSource() {
        return UsageSource.valueOf(source);
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public int getPiiRedacted() {
        return piiRedacted;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}