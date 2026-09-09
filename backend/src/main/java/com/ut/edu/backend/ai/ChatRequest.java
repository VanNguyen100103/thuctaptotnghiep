package com.ut.edu.backend.ai;

import com.ut.edu.backend.validation.SafeText;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for both chat endpoints (POST /stores/{slug}/chat and POST /assistant/chat). sessionId is null on the first message of a conversation - the server mints one. */
public record ChatRequest(
        String sessionId,
        @NotBlank(message = "Message is required")
        @Size(max = 500, message = "Message must be at most 500 characters")
        @SafeText(message = "Message contains dangerous content")
        String message) {
}
