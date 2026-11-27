package com.noypi.docguardxray.document;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.noypi.docguardxray.common.ActiveDocumentLookup;
import com.noypi.docguardxray.common.ActiveTenantLookup;
import com.noypi.docguardxray.document.DocumentDtos.CreateDocumentRequest;
import com.noypi.docguardxray.document.DocumentDtos.DocumentResponse;
import com.noypi.docguardxray.document.DocumentDtos.DocumentSummaryResponse;
import com.noypi.docguardxray.document.DocumentDtos.UpdateDocumentRequest;
import com.noypi.docguardxray.tenant.TenantContext;
import com.noypi.docguardxray.utils.HashUtils;

@Service
public class DocumentService implements ActiveDocumentLookup {

    private final DocumentRepository documentRepository;
    private final ActiveTenantLookup activeTenantLookup;
    private final VectorStore vectorStore;
    private final JdbcClient jdbcClient;
    private final TokenTextSplitter textSplitter;

    public DocumentService(
            DocumentRepository documentRepository,
            ActiveTenantLookup activeTenantLookup,
            VectorStore vectorStore,
            JdbcClient jdbcClient,
            TokenTextSplitter textSplitter) {
        this.documentRepository = documentRepository;
        this.activeTenantLookup = activeTenantLookup;
        this.vectorStore = vectorStore;
        this.jdbcClient = jdbcClient;
        this.textSplitter = textSplitter;
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryResponse> listDocuments(Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        return this.documentRepository.findAllByTenantId(tenantId, pageable).stream()
                .map(DocumentSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId) {
        UUID tenantId = TenantContext.getTenantId();
        Document doc = this.documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        return DocumentResponse.from(doc);
    }
    
    @Transactional
    public DocumentResponse createDocument(CreateDocumentRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        String contentHash = HashUtils.sha256Hex(request.content());

        Optional<Document> existingByHash = this.documentRepository.findByTenantIdAndContentHash(tenantId, contentHash);
        if (existingByHash.isPresent()) {
            return DocumentResponse.from(existingByHash.get());
        }

        if (documentRepository.existsByTenantIdAndSlug(tenantId, request.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document slug already exists");
        }

        Document saved = documentRepository.saveAndFlush(new Document(
                tenantId,
                request.slug(),
                request.title(),
                request.content(),
                contentHash,
                request.isPublic()));

        indexChunks(saved);

        return DocumentResponse.from(saved);
    }
    
    @Transactional
    public DocumentResponse updateDocument(UUID documentId, UpdateDocumentRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        Document doc = this.documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        String newHash = HashUtils.sha256Hex(request.content());
        boolean contentChanged = !doc.getContentHash().equals(newHash);

        doc.setTitle(request.title());
        doc.setPublic(request.isPublic());

        if (contentChanged) {
            doc.setContent(request.content());
            doc.setContentHash(newHash);
            doc.setVersion(doc.getVersion() + 1);
        }

        Document saved = this.documentRepository.saveAndFlush(doc);
        indexChunks(saved);

        // atomic chunk update inside of @Transactional boundary
        if (contentChanged) {
            deleteChunksForDocument(saved.getId());
            indexChunks(saved);
        }

        return DocumentResponse.from(saved);
    }

    @Transactional
    public void deleteDocument(UUID documentId) {
        UUID tenantId = TenantContext.getTenantId();
        Document doc = this.documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        // no need to call deleteChunksForDocument, will cascade on delete
        this.documentRepository.delete(doc);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getPublicDocument(String tenantSlug, String docSlug) {
        UUID tenantId = this.activeTenantLookup.findActiveTenantIdBySlug(tenantSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base not found"));

        Document doc = this.documentRepository.findByTenantIdAndSlugAndIsPublicTrue(tenantId, docSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        return DocumentResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public List<org.springframework.ai.document.Document> queryDocs(String query) {
        return this.vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(4)
                        .similarityThreshold(0.4)
                        .build());
    }


    private void indexChunks(Document doc) {
        var sourceDoc = new org.springframework.ai.document.Document(
                            doc.getContent(),
                            Map.of(
                                "tenant_id", doc.getTenantId().toString(),
                                "document_id", doc.getId().toString(),
                                "slug", doc.getSlug(),
                                "title", doc.getTitle(),
                                "is_public", doc.isPublic()
                            )
        );

        List<org.springframework.ai.document.Document> chunks = textSplitter.apply(List.of(sourceDoc));
        if (!chunks.isEmpty()) {
            this.vectorStore.add(chunks);
        }
    }

    private void deleteChunksForDocument(UUID documentId) {
        this.jdbcClient.sql("DELETE FROM vector_store WHERE document_id = :documentId")
                .param("documentId", documentId)
                .update();
    }
}
