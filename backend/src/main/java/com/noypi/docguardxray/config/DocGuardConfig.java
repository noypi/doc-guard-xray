package com.noypi.docguardxray.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docguard")
public record DocGuardConfig(Cors cors, Auth auth) {

    public record Cors(List<String> allowedOrigins) {}

    public record Auth(boolean demoSwitchEnabled) {}
    
}
