package com.noypi.docguardxray.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.noypi.docguardxray.ai.PiiRedactionAdvisor;
import com.noypi.docguardxray.ai.TokenQuotaAdvisor;

@Configuration 
public class AiConfig {
    
    @Bean 
    public ChatClient chatClient(
        ChatClient.Builder builder,
        TokenQuotaAdvisor tokenQuotaAdvisor,
        PiiRedactionAdvisor piiRedactionAdvisor
    ) {
        return builder
                .defaultAdvisors(tokenQuotaAdvisor, piiRedactionAdvisor)
                .build();
    }
    
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return TokenTextSplitter.builder().build();
    }
}
