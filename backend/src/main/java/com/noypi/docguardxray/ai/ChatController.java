package com.noypi.docguardxray.ai;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/v1/chat")
@Tag (name = "AI Chat", description = "Multi-tenant RAG chat with PII redaction and X-Ray telemetry")
public class ChatController {
    
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final RagChatService ragChatService;

    public ChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    @PostMapping
    @Operation(summary = "Synchronous RAG chat", description = "Returns full answer and X-Ray trace (ideal for n8n HTTP nodes)")
    public ChatDtos.ChatResponse chat(@Valid @RequestBody ChatDtos.ChatRequest request) {
        return this.ragChatService.chat(request);
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream RAG chat (Dual-Event SSE)", description = "Streams 'token' events followed by a final 'xray' trace event")
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ChatDtos.ChatRequest request) {
        log.debug("request chat/stream");
        return this.ragChatService.streamChat(request);
    }
    
    
}
