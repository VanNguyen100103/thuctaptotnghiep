package com.ut.edu.backend.shipping.goship;

/**
 * Anything Goship refuses or cannot answer. Controllers turn this into a
 * 502, since it means the upstream carrier aggregator failed rather than the
 * caller sending something wrong.
 */
public class GoshipApiException extends RuntimeException {

    public GoshipApiException(String message) {
        super(message);
    }

    public GoshipApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
