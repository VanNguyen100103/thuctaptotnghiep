package com.ut.edu.backend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * AI Configuration for Google Gemini API
 *
 * Gemini doesn't have OpenAI-compatible endpoint, so we use direct REST API calls
 * This is FREE and doesn't require billing
 *
 * TIMEOUT CONFIGURATION:
 * - Connect timeout: 10 seconds (time to establish connection)
 * - Read timeout: 60 seconds (time to wait for response from Gemini)
 */
@Configuration
public class AIConfiguration {

    /**
     * RestTemplate with timeout configuration for Gemini API
     *
     * Timeouts prevent hanging requests when:
     * - Gemini API is slow to respond
     * - Network issues occur
     * - AI processing takes too long
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))  // Connection timeout: 10s
                .setReadTimeout(Duration.ofSeconds(60))     // Read timeout: 60s (Gemini can be slow)
                .build();
    }
}
