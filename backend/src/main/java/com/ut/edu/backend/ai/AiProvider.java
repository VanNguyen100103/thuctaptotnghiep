package com.ut.edu.backend.ai;

import java.util.List;

/** One LLM backend (Gemini, Groq, ...) capable of tool-calling chat completion. */
public interface AiProvider {

    /** Short lowercase name reported back in ChatResponse#provider, e.g. "gemini". */
    String name();

    /**
     * Generates the next turn given the conversation so far.
     *
     * @throws AiProviderException on any upstream failure (rate limit, 5xx, timeout, missing config) -
     *         callers use this to fall back to another provider.
     */
    AiChatResult generateReply(String systemPrompt, List<AiChatMessage> history, List<AiTool> tools);
}
