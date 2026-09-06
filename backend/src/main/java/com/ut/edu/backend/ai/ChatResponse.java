package com.ut.edu.backend.ai;

/** provider is "gemini" or "groq" - lets the frontend/tests confirm which one actually answered. */
public record ChatResponse(String sessionId, String reply, String provider) {
}
