package com.ut.edu.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * DeepSeek API client - the only AI provider for this application
 * DeepSeek API is compatible with OpenAI API format
 */
@Component
@Slf4j
public class DeepSeekService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.model:deepseek-chat}")
    private String model;

    @Value("${deepseek.api.temperature:0.7}")
    private Double temperature;

    @Value("${deepseek.api.max-tokens:1000}")
    private Integer maxTokens;

    public DeepSeekService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Call DeepSeek API using Chat Completions endpoint (OpenAI-compatible)
     * DeepSeek API URL: https://api.deepseek.com/v1/chat/completions
     */
    public String callDeepSeek(String prompt) {
        try {
            String url = "https://api.deepseek.com/v1/chat/completions";

            // Build request body according to DeepSeek API spec (same as OpenAI)
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);

            // Create message array
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);
            requestBody.put("messages", messages);

            // Set headers with API key
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("🔵 Calling DeepSeek API with model: {} | URL: {} | API Key: {}...",
                    model, url, apiKey != null ? apiKey.substring(0, Math.min(10, apiKey.length())) + "***" : "NULL");

            String response = restTemplate.postForObject(url, entity, String.class);

            log.info("🔵 DeepSeek raw response: {}", response != null ? response.substring(0, Math.min(200, response.length())) : "NULL");

            // Parse response (same format as OpenAI)
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode messageNode = firstChoice.path("message");
                String content = messageNode.path("content").asText();

                log.info("✅ DeepSeek API call successful - Content length: {}", content != null ? content.length() : 0);
                return content;
            }

            log.warn("⚠️ DeepSeek API returned empty response");
            return "Xin lỗi, tôi không thể trả lời câu hỏi này.";

        } catch (Exception e) {
            log.error("❌ Error calling DeepSeek API - Error type: {} | Message: {} | Full trace:",
                    e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("DeepSeek API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if DeepSeek service is available
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.startsWith("${");
    }
}
