package com.noypi.docguardxray.document;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class Document {
    
    @Id
    @GeneratedValue (strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "slug", nullable = false, length = 160)
    private String slug;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Document() {}
    
    public Document(UUID tenantId, String slug, String title, String content, String contentHash, boolean isPublic) {
        this.tenantId = tenantId;
        this.slug = slug;
        this.title = title;
        this.content = content;
        this.contentHash = contentHash;
        this.isPublic = isPublic;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public int getVersion() {
        return version;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
