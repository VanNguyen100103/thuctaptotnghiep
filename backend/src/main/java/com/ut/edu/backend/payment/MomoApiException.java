package com.ut.edu.backend.payment;

/** Wraps a failure calling MoMo's payment API, mirroring PayPalApiException's shape. */
public class MomoApiException extends RuntimeException {

    public MomoApiException(String message) {
        super(message);
    }

    public MomoApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
