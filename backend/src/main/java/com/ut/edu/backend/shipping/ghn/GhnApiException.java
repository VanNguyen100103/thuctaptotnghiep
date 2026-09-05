package com.ut.edu.backend.shipping.ghn;

/** Wraps a failure calling GHN's API, mirroring MomoApiException/PayPalApiException's shape. */
public class GhnApiException extends RuntimeException {

    public GhnApiException(String message) {
        super(message);
    }

    public GhnApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
