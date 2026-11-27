package com.noypi.docguardxray.ai;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.noypi.docguardxray.constant.AdvisorOrders;

import reactor.core.publisher.Flux;

@Component 
public class PiiRedactionAdvisor implements CallAdvisor, StreamAdvisor{
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(\\+?\\d{1,3}[\\s.-]?)?(\\(?\\d{3,4}\\)?[\\s.-]?)\\d{3,4}[\\s.-]?\\d{4}");

    private static final Pattern CARD_OR_ID_PATTERN =
            Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b|\\b\\d{3}-\\d{2}-\\d{4}\\b");

    @Override
    public String getName() {
        return "PiiRedactionAdvisor";
    }

    @Override
    public int getOrder() {
        return AdvisorOrders.PII_REDACTION;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(maskRequest(request));
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(maskRequest(request));
    }

    private ChatClientRequest maskRequest(ChatClientRequest request) {
        UserMessage userMessage = request.prompt().getUserMessage();
        if (userMessage == null || userMessage.getText() == null) {
            return request;
        }

        XRayState xRayState = getXRayState(request);
        String original = userMessage.getText();
        AtomicInteger count = new AtomicInteger(0);

        String masked = replaceWithCounter(EMAIL_PATTERN, original, "EMAIL", count);
        masked = replaceWithCounter(CARD_OR_ID_PATTERN, masked, "ID", count);
        masked = replaceWithCounter(PHONE_PATTERN, masked, "PHONE", count);

        xRayState.recordPii(original, masked, count.get());

        if (count.get() == 0) {
            return request;
        }

        Prompt updatedPrompt = request.prompt().augmentUserMessage(masked);

        return request.mutate().prompt(updatedPrompt).build();
    }

    private String replaceWithCounter(
            Pattern pattern,
            String input,
            String label,
            AtomicInteger totalCount
    ) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        int localIndex = 1;

        while (matcher.find()) {
            totalCount.incrementAndGet();
            matcher.appendReplacement(sb, "[" + label + "_" + localIndex++ + "]");
        }

        matcher.appendTail(sb);

        return sb.toString();
    }
    
    private XRayState getXRayState(ChatClientRequest request) {
        XRayState xRayState = (XRayState) request.context().get(XRayState.class.getName());
        if (xRayState == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "xray was not set");
        }

        return xRayState;
    }
    
}
