package com.noypi.docguardxray.common;

import java.util.List;

import org.springframework.ai.document.Document;

public interface ActiveDocumentLookup {
    List<Document> queryDocs(String query);
}
