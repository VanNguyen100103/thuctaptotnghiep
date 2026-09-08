package com.ut.edu.backend.auth;

/**
 * The token the browser presented is not a valid Google identity for this
 * app. Always answered as a 401 - it means the caller is not who they say
 * they are, never that something on our side broke.
 */
public class GoogleSignInException extends RuntimeException {

    public GoogleSignInException(String message) {
        super(message);
    }

    public GoogleSignInException(String message, Throwable cause) {
        super(message, cause);
    }
}
