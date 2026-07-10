package com.ut.edu.backend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * AI Configuration - HTTP client for the DeepSeek API
 *
 * TIMEOUT CONFIGURATION:
 * - Connect timeout: 10 seconds (time to establish connection)
 * - Read timeout: 60 seconds (AI responses can be slow)
 */
@Configuration
public class AIConfiguration {

    /**
     * RestTemplate with timeout configuration for AI API calls
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))  // Connection timeout: 10s
                .setReadTimeout(Duration.ofSeconds(60))     // Read timeout: 60s (DeepSeek can be slow)
                .build();
    }
}
