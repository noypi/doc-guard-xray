package com.noypi.docguardxray.document;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.noypi.docguardxray.document.DocumentDtos.DocumentResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController
@RequestMapping("/api/v1/public/kb")
public class PublicKbController {
    
    private final DocumentService documentService;

    public PublicKbController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{tenantSlug}/{docSlug}")
    @Transactional(readOnly = true)
    public ResponseEntity<DocumentResponse> getPublicDocument(
            @PathVariable String tenantSlug,
            @PathVariable String docSlug
    ) {
        return ResponseEntity.ok(this.documentService.getPublicDocument(tenantSlug, docSlug));
    }
}
