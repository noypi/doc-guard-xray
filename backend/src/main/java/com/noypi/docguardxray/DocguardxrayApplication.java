package com.noypi.docguardxray;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.noypi.docguardxray.config.DocGuardConfig;

@SpringBootApplication
@EnableConfigurationProperties(DocGuardConfig.class)
public class DocguardxrayApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocguardxrayApplication.class, args);
	}

}
