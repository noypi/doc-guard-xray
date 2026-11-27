package com.noypi.docguardxray.document;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.noypi.docguardxray.document.DocumentDtos.CreateDocumentRequest;
import com.noypi.docguardxray.document.DocumentDtos.DocumentResponse;
import com.noypi.docguardxray.document.DocumentDtos.DocumentSummaryResponse;
import com.noypi.docguardxray.document.DocumentDtos.UpdateDocumentRequest;

import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;


@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<List<DocumentSummaryResponse>> listDocuments(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(this.documentService.listDocuments(pageable));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(this.documentService.getDocument(id));
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> createDocument(
            @Valid @RequestBody CreateDocumentRequest request
    ) {
        log.info("Creating doc slug={}", request.slug());
        DocumentResponse created = this.documentService.createDocument(request);
        URI location = URI.create("/api/v1/documents/" + created.id());

        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> updateDocument(
        @PathVariable UUID id, 
        @Valid @RequestBody UpdateDocumentRequest request) {
        return ResponseEntity.ok(this.documentService.updateDocument(id, request));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        this.documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
