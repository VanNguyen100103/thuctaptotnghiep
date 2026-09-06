package com.ut.edu.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback AI provider, used when GeminiProvider throws AiProviderException.
 * Groq exposes an OpenAI-compatible /chat/completions endpoint (tools /
 * tool_calls / role:"tool"), hosting open models like Llama.
 */
@Component
@Slf4j
public class GroqProvider implements AiProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public GroqProvider(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${groq.api.key}") String apiKey,
            @Value("${groq.api.base-url}") String baseUrl,
            @Value("${groq.api.model}") String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String name() {
        return "groq";
    }

    @Override
    public AiChatResult generateReply(String systemPrompt, List<AiChatMessage> history, List<AiTool> tools) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException("GROQ_API_KEY is not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", toMessages(systemPrompt, history));
        body.put("tools", toToolDefs(tools));
        body.put("tool_choice", "auto");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<?, ?> response;
        try {
            response = restTemplate.exchange(baseUrl + "/chat/completions", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class).getBody();
        } catch (RestClientException e) {
            throw new AiProviderException("Groq request failed: " + e.getMessage(), e);
        }

        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private AiChatResult parseResponse(Map<?, ?> response) {
        if (response == null) {
            throw new AiProviderException("Groq returned an empty response");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new AiProviderException("Groq returned no choices: " + response);
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");

        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<AiChatMessage.ToolCallRequest> calls = new ArrayList<>();
            for (Map<String, Object> tc : toolCalls) {
                String id = (String) tc.get("id");
                Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                String fname = (String) fn.get("name");
                calls.add(new AiChatMessage.ToolCallRequest(id, fname, parseArgs((String) fn.get("arguments"))));
            }
            return AiChatResult.calls(calls);
        }

        Object content = message.get("content");
        return AiChatResult.text(content == null ? "" : content.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse Groq tool-call arguments '{}': {}", argumentsJson, e.getMessage());
            return Map.of();
        }
    }

    private List<Map<String, Object>> toMessages(String systemPrompt, List<AiChatMessage> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (AiChatMessage m : history) {
            switch (m.role()) {
                case USER -> messages.add(Map.of("role", "user", "content", m.text()));
                case MODEL -> messages.add(assistantMessage(m));
                case TOOL_RESULT -> messages.add(toolMessage(m.toolResult()));
            }
        }
        return messages;
    }

    private Map<String, Object> assistantMessage(AiChatMessage m) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            msg.put("content", null);
            msg.put("tool_calls", m.toolCalls().stream()
                    .<Map<String, Object>>map(c -> Map.of(
                            "id", c.id(),
                            "type", "function",
                            "function", Map.of("name", c.name(), "arguments", writeJson(c.args()))
                    ))
                    .toList());
        } else {
            msg.put("content", m.text() == null ? "" : m.text());
        }
        return msg;
    }

    private Map<String, Object> toolMessage(AiChatMessage.ToolCallResult r) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", r.id());
        msg.put("content", writeJson(r.result()));
        return msg;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<Map<String, Object>> toToolDefs(List<AiTool> tools) {
        return tools.stream()
                .<Map<String, Object>>map(t -> Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", t.name(),
                                "description", t.description(),
                                "parameters", t.parameters()
                        )
                ))
                .toList();
    }
}
