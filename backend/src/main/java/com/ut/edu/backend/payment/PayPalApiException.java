package com.ut.edu.backend.payment;

/**
 * Wraps a failure calling PayPal's REST API (Subscriptions, Catalog Products,
 * webhook verification) via {@link PayPalRestClient}, so callers don't need
 * to know about the underlying HTTP client.
 */
public class PayPalApiException extends RuntimeException {

    public PayPalApiException(String message) {
        super(message);
    }

    public PayPalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
