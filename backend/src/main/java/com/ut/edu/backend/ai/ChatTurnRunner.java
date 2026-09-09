package com.ut.edu.backend.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The provider-agnostic half of a chat turn, shared by the two assistants:
 * StoreChatService (storefront sales bot, tool-calling) and
 * PlatformChatService (homepage Tryum consultant, no tools). Tries Gemini,
 * falls back to Groq, runs whatever tool calls the model asks for, and keeps
 * the stored history bounded.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatTurnRunner {

    private final GeminiProvider geminiProvider;
    private final GroqProvider groqProvider;
    private final ChatToolExecutor toolExecutor;

    @Value("${ai.chat.max-tool-iterations:4}")
    private int maxToolIterations;

    /** Applied as a turn count (see trimHistory), not a raw message count - trimming mid tool-call/tool-result pair would corrupt the next provider call. */
    @Value("${ai.chat.max-history-messages:12}")
    private int maxHistoryTurns;

    /** One finished turn: what the model said, and which provider actually said it. */
    public record Turn(AiChatResult result, String provider) {
    }

    /**
     * Runs one turn on Gemini, falling back to Groq if it fails. {@code context}
     * only labels the fallback log line (a store slug, "homepage", ...).
     *
     * Mutates {@code history} in place with any tool-call/tool-result pairs, so
     * the fallback provider reuses tool results already fetched this turn.
     */
    public Turn run(String systemPrompt, List<AiChatMessage> history, List<AiTool> tools, String context) {
        try {
            return new Turn(runWithProvider(geminiProvider, systemPrompt, history, tools), geminiProvider.name());
        } catch (AiProviderException primaryFailure) {
            log.warn("Gemini failed for {}: {} - falling back to Groq", context, primaryFailure.getMessage());
            return new Turn(runWithProvider(groqProvider, systemPrompt, history, tools), groqProvider.name());
        }
    }

    /**
     * Keeps asking one provider to respond, executing any tool calls it requests,
     * until it returns a final answer or maxToolIterations is exceeded. With an
     * empty tool list the model is never offered tools, so this returns on the
     * first pass.
     */
    private AiChatResult runWithProvider(AiProvider provider, String systemPrompt, List<AiChatMessage> history,
                                         List<AiTool> tools) {
        for (int i = 0; i < maxToolIterations; i++) {
            AiChatResult result = provider.generateReply(systemPrompt, history, tools);
            if (!result.hasToolCalls()) {
                return result;
            }
            history.add(AiChatMessage.modelToolCalls(result.toolCalls()));
            for (AiChatMessage.ToolCallRequest call : result.toolCalls()) {
                Object toolResult = toolExecutor.execute(call.name(), call.args());
                history.add(AiChatMessage.toolResult(call.id(), call.name(), toolResult));
            }
        }
        throw new AiProviderException(provider.name() + " exceeded max tool-call iterations without a final answer");
    }

    /** Keeps the last N user turns intact (never cuts inside a tool-call/tool-result block, which would break the next provider call). */
    public List<AiChatMessage> trimHistory(List<AiChatMessage> history) {
        List<Integer> userTurnStarts = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).role() == AiChatMessage.Role.USER) {
                userTurnStarts.add(i);
            }
        }
        if (userTurnStarts.size() <= maxHistoryTurns) {
            return history;
        }
        int fromIndex = userTurnStarts.get(userTurnStarts.size() - maxHistoryTurns);
        return new ArrayList<>(history.subList(fromIndex, history.size()));
    }
}
