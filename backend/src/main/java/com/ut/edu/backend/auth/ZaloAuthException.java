package com.ut.edu.backend.auth;

/**
 * The Zalo sign-in attempt cannot be completed - an expired state, a code
 * Zalo refused, or an unreachable Zalo. Distinct from "this Zalo account is
 * not linked to anyone here", which is a normal answer rather than a failure.
 */
public class ZaloAuthException extends RuntimeException {

    public ZaloAuthException(String message) {
        super(message);
    }

    public ZaloAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
