package com.ut.edu.backend.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Primary AI provider. Calls Gemini's generateContent REST endpoint with
 * function-calling (tools) enabled. Any upstream failure (missing key, rate
 * limit, 5xx, timeout) is mapped to AiProviderException so StoreChatService
 * can fall back to Groq.
 */
@Component
@Slf4j
public class GeminiProvider implements AiProvider {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public GeminiProvider(
            RestTemplate restTemplate,
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.api.base-url}") String baseUrl,
            @Value("${gemini.api.model}") String model) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public AiChatResult generateReply(String systemPrompt, List<AiChatMessage> history, List<AiTool> tools) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException("GEMINI_API_KEY is not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", toContents(history));
        body.put("tools", List.of(Map.of("functionDeclarations", toFunctionDeclarations(tools))));

        String url = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;
        Map<?, ?> response;
        try {
            response = restTemplate.postForObject(url, body, Map.class);
        } catch (RestClientException e) {
            throw new AiProviderException("Gemini request failed: " + e.getMessage(), e);
        }

        return parseResponse(response);
    }

    @SuppressWarnings("unchecked")
    private AiChatResult parseResponse(Map<?, ?> response) {
        if (response == null) {
            throw new AiProviderException("Gemini returned an empty response");
        }
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new AiProviderException("Gemini returned no candidates (promptFeedback=" + response.get("promptFeedback") + ")");
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = content == null ? List.of() : (List<Map<String, Object>>) content.get("parts");

        StringBuilder text = new StringBuilder();
        List<AiChatMessage.ToolCallRequest> calls = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> part : parts) {
            if (part.containsKey("functionCall")) {
                Map<String, Object> fc = (Map<String, Object>) part.get("functionCall");
                String fname = (String) fc.get("name");
                Map<String, Object> args = (Map<String, Object>) fc.getOrDefault("args", Map.of());
                calls.add(new AiChatMessage.ToolCallRequest(fname + "#" + (i++), fname, args));
            } else if (part.get("text") != null) {
                text.append((String) part.get("text"));
            }
        }

        return calls.isEmpty() ? AiChatResult.text(text.toString()) : AiChatResult.calls(calls);
    }

    private List<Map<String, Object>> toContents(List<AiChatMessage> history) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (AiChatMessage m : history) {
            switch (m.role()) {
                case USER -> contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", m.text()))));
                case MODEL -> contents.add(modelContent(m));
                case TOOL_RESULT -> contents.add(toolResultContent(m.toolResult()));
            }
        }
        return contents;
    }

    private Map<String, Object> modelContent(AiChatMessage m) {
        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            List<Map<String, Object>> parts = m.toolCalls().stream()
                    .<Map<String, Object>>map(c -> Map.of("functionCall", Map.of("name", c.name(), "args", c.args())))
                    .toList();
            return Map.of("role", "model", "parts", parts);
        }
        return Map.of("role", "model", "parts", List.of(Map.of("text", m.text() == null ? "" : m.text())));
    }

    private Map<String, Object> toolResultContent(AiChatMessage.ToolCallResult r) {
        Object responsePayload = r.result() instanceof Map ? r.result() : Map.of("result", String.valueOf(r.result()));
        return Map.of("role", "function", "parts", List.of(
                Map.of("functionResponse", Map.of("name", r.name(), "response", responsePayload))
        ));
    }

    private List<Map<String, Object>> toFunctionDeclarations(List<AiTool> tools) {
        return tools.stream()
                .map(t -> {
                    Map<String, Object> decl = new LinkedHashMap<>();
                    decl.put("name", t.name());
                    decl.put("description", t.description());
                    decl.put("parameters", upperCaseSchemaTypes(t.parameters()));
                    return decl;
                })
                .toList();
    }

    /** Gemini's function-declaration schema uses OpenAPI-style uppercase types (OBJECT, STRING, ...); AiToolCatalog uses standard lowercase JSON Schema. */
    private Object upperCaseSchemaTypes(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = (String) e.getKey();
                out.put(key, key.equals("type") && e.getValue() instanceof String s
                        ? s.toUpperCase(Locale.ROOT)
                        : upperCaseSchemaTypes(e.getValue()));
            }
            return out;
        } else if (node instanceof List<?> list) {
            return list.stream().map(this::upperCaseSchemaTypes).toList();
        }
        return node;
    }
}
