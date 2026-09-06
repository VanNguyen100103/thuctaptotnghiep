package com.ut.edu.backend.ai;

/** Thrown by an AiProvider on any upstream failure - StoreChatService catches this to fall back to the next provider. */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
