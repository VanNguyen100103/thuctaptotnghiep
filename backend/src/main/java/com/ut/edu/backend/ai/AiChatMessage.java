package com.ut.edu.backend.ai;

import java.util.List;
import java.util.Map;

/**
 * One turn of a chat conversation, in a shape both GeminiProvider and
 * GroqProvider translate into their own wire format. Persisted verbatim to
 * Redis by ChatSessionService and replayed to whichever provider handles the
 * next turn - this is what lets StoreChatService fall back from Gemini to
 * Groq mid tool-call-loop without losing already-fetched tool results.
 */
public record AiChatMessage(
        Role role,
        String text,
        List<ToolCallRequest> toolCalls,
        ToolCallResult toolResult
) {
    public enum Role { USER, MODEL, TOOL_RESULT }

    public record ToolCallRequest(String id, String name, Map<String, Object> args) {
    }

    public record ToolCallResult(String id, String name, Object result) {
    }

    public static AiChatMessage userText(String text) {
        return new AiChatMessage(Role.USER, text, null, null);
    }

    public static AiChatMessage modelText(String text) {
        return new AiChatMessage(Role.MODEL, text, null, null);
    }

    public static AiChatMessage modelToolCalls(List<ToolCallRequest> calls) {
        return new AiChatMessage(Role.MODEL, null, calls, null);
    }

    public static AiChatMessage toolResult(String id, String name, Object result) {
        return new AiChatMessage(Role.TOOL_RESULT, null, null, new ToolCallResult(id, name, result));
    }
}
