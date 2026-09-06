package com.ut.edu.backend.ai;

import java.util.List;

/** Result of one provider call: either a final text answer, or tool calls the caller must execute before asking again - never both. */
public record AiChatResult(String finalText, List<AiChatMessage.ToolCallRequest> toolCalls) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static AiChatResult text(String text) {
        return new AiChatResult(text, null);
    }

    public static AiChatResult calls(List<AiChatMessage.ToolCallRequest> calls) {
        return new AiChatResult(null, calls);
    }
}
