package com.noypi.docguardxray.ai;

import java.util.List;
import java.util.UUID;

import com.noypi.docguardxray.constant.UsageSource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ChatDtos {
    
    private ChatDtos() {}
    
    public record ChatRequest(
            @NotBlank(message = "Prompt must not be blank")
            @Size(max = 4000, message = "Prompt must not exceed 4000 characters")
            String prompt,

            // WEB | API 
            // (default: WEB)
            UsageSource source
    ) {}
    
    public record Citation(
            UUID documentId,
            String title,
            String slug,
            String snippet,
            Double score
    ) {}
    
    public record XRayTrace(
            String traceId,
            String originalPrompt,
            String maskedPrompt,
            int piiRedactedCount,
            List<Citation> citations,
            int tokensUsed,
            int remainingMonthlyQuota,
            long latencyMs
    ) {}
    
    public record ChatResponse(
            String answer,
            XRayTrace xray
    ) {}
}
