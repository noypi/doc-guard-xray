package com.noypi.docguardxray;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.resilience.annotation.EnableResilientMethods;

import com.noypi.docguardxray.config.DocGuardConfig;

@EnableConfigurationProperties({ DocGuardConfig.class })
@EnableResilientMethods
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DocGuardXrayApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocGuardXrayApplication.class, args);
	}

}
