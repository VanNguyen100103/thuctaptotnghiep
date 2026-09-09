export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}

export interface ChatRequest {
  sessionId: string | null;
  message: string;
}

/** provider is "gemini" or "groq" - which one actually answered this turn. */
export interface ChatResponse {
  sessionId: string;
  reply: string;
  provider: string;
}
