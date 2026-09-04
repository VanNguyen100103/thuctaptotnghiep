package com.ut.edu.backend.payment;

/** Wraps a SePay integration failure (e.g. missing config), mirroring MomoApiException's shape. */
public class SePayApiException extends RuntimeException {

    public SePayApiException(String message) {
        super(message);
    }

    public SePayApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
