package com.noypi.docguardxray.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DocumentDtos {
    
    private DocumentDtos() {}
    
    public record CreateDocumentRequest(
        @NotBlank(message = "Slug is required")
        @Size(max = 160, message = "Slug must be at most 160 characters")
        String slug,

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @NotBlank(message = "Content is required")
        String content,

        boolean isPublic
    ) {
        public CreateDocumentRequest {
            slug = slug == null ? null : slug.trim();
            title = title == null ? null : title.trim();
        }
    }

    public record UpdateDocumentRequest(
        @NotBlank(message = "Title is required") 
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @NotBlank(message = "Content is required") 
        String content,

        boolean isPublic
    ) {
        public UpdateDocumentRequest {
            title = title == null ? null : title.trim();
        }
    }

    public record DocumentResponse(
            UUID id,
            UUID tenantId,
            int version,
            String slug,
            String title,
            String content,
            String contentHash,
            boolean isPublic,
            Instant createdAt,
            Instant updatedAt) {
        public static DocumentResponse from(Document document) {
            return new DocumentResponse(
                    document.getId(),
                    document.getTenantId(),
                    document.getVersion(),
                    document.getSlug(),
                    document.getTitle(),
                    document.getContent(),
                    document.getContentHash(),
                    document.isPublic(),
                    document.getCreatedAt(),
                    document.getUpdatedAt());
        }
    }

    public record DocumentSummaryResponse(
            UUID id,
            int version,
            String slug,
            String title,
            boolean isPublic,
            Instant updatedAt) {
        public static DocumentSummaryResponse from(Document document) {
            return new DocumentSummaryResponse(
                    document.getId(),
                    document.getVersion(),
                    document.getSlug(),
                    document.getTitle(),
                    document.isPublic(),
                    document.getUpdatedAt());
        }
    }
    
    public record PublicKbArticleReponse(
            String tenantSlug,
            String slug,
            String title,
            String content,
            int version,
            Instant updatedAt
    ) {
    }

}
