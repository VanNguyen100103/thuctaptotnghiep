package com.ut.edu.backend.ai;

import java.util.Map;

/**
 * Provider-agnostic tool (function) definition. {@code parameters} is a
 * standard JSON Schema object (lowercase types: "object", "string", ...) -
 * GroqProvider sends it as-is (OpenAI-compatible), GeminiProvider upper-cases
 * the "type" values to match Gemini's OpenAPI-style schema.
 */
public record AiTool(String name, String description, Map<String, Object> parameters) {
}
