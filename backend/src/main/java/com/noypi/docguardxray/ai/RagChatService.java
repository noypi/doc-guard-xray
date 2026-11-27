package com.noypi.docguardxray.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.noypi.docguardxray.common.ActiveDocumentLookup;
import com.noypi.docguardxray.constant.UsageSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Service 
public class RagChatService {

    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);
    private static final String SYSTEM_PROMPT = """
        Your are DocGuard AI, a secure enterprise assistant.
        Answer the user's question using ONLY the provided context snippets.
        If the answer is not in the context, say clearly that you don't have that information.
        Cite the document title when referencing facts.

        Context:
        %s
    """;

    private final ChatClient chatClient;
    private final ActiveDocumentLookup activeDocumentLookup;
    private final ObjectMapper objectMapper;

    public RagChatService(
            ChatClient chatClient,
            ActiveDocumentLookup activeDocumentLookup,
            ObjectMapper objectMapper
    ) {
        this.chatClient = chatClient;
        this.activeDocumentLookup = activeDocumentLookup;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @Retryable(
        maxRetries = 2,
        excludes = ResponseStatusException.class,
        delay = 500,
        multiplier = 2.0,
        maxDelay = 5000,
        jitter = 100
    )
    public ChatDtos.ChatResponse chat(ChatDtos.ChatRequest request) {
        long startMs = System.currentTimeMillis();
        UsageSource source = (request.source() != null)
                ? request.source()
                : UsageSource.API;
        XRayState xRayState = new XRayState(source);
        String contextBlock = retrieveAndRecordCitations(request.prompt(), xRayState);

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(contextBlock))
                .user(request.prompt())
                .advisors(spec -> spec.param(XRayState.class.getName(), xRayState))
                .call()
                .content();

        return new ChatDtos.ChatResponse(answer, xRayState.toTrace(startMs));
    }

    public Flux<ServerSentEvent<String>> streamChat(ChatDtos.ChatRequest request) {
        long startMs = System.currentTimeMillis();
        UsageSource source = (request.source() != null)
                ? request.source()
                : UsageSource.WEB;
        // recordTokens is set at TokenQuotaAdvisor
        XRayState xRayState = new XRayState(source);
        String contextBlock = retrieveAndRecordCitations(request.prompt(), xRayState);

        Flux<ServerSentEvent<String>> tokenStream = this.chatClient.prompt()
                .system((SYSTEM_PROMPT.formatted(contextBlock)))
                .user(request.prompt())
                .advisors(spec -> spec.param(XRayState.class.getName(), xRayState))
                .stream()
                .content()
                .map(token -> ServerSentEvent.<String>builder()
                        .event("token")
                        .data(toJson(Map.of("text", token)))
                        .build());

        Mono<ServerSentEvent<String>> xRayEvent = Mono.fromSupplier(() -> ServerSentEvent.<String>builder()
                .event("xray")
                .data(toJson(xRayState.toTrace(startMs)))
                .build());

        Flux<ServerSentEvent<String>> safeTokenStream = tokenStream
                .onErrorResume(ex -> {
                    if (ex instanceof ResponseStatusException) {
                        return Flux.error(ex);
                    }

                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("token")
                            .data(toJson(Map.of("text", "\n[AI provider temporarily busy. Please try again.]")))
                            .build());
                });

        return safeTokenStream.concatWith(xRayEvent);
    }

    private List<ChatDtos.Citation> retrieveCitations(List<Document> docs) {
        return docs.stream()
                .map(doc -> {
                    Map<String, Object> meta = doc.getMetadata();
                    UUID docId = meta.get("document_id") != null
                            ? UUID.fromString(meta.get("document_id").toString())
                            : null;
                    String title = String.valueOf(meta.getOrDefault("title", "Untitled"));
                    String slug = String.valueOf(meta.getOrDefault("slug", ""));
                    String text = doc.getText() != null ? doc.getText() : "";
                    String snippet = text.length() > 180
                            ? text.substring(0, 180) + "..."
                            : text;

                    return new ChatDtos.Citation(docId, title, slug, snippet, doc.getScore());
                }).toList();
    }

    private String retrieveAndRecordCitations(String query, XRayState xRayState) {
        List<Document> docs = this.activeDocumentLookup.queryDocs(query);
        List<ChatDtos.Citation> citations = retrieveCitations(docs);

        xRayState.recordCitations(citations);

        if (citations.isEmpty()) {
            return "No matching documents found.";
        }

        return docs.stream()
                .map(d -> "[Source: " + d.getMetadata().getOrDefault("title", "Doc") + "]\n" + d.getText())
                .collect(Collectors.joining("\n\n---\n\n"));
    }
    
    private String toJson(Object value) {
        try {
            return this.objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
