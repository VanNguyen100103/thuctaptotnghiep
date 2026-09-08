package com.ut.edu.backend.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * The ID token Google gave the browser. Nothing else is accepted from the
 * page - the email, name and everything else are read out of the token after
 * its signature checks out, never taken from the request.
 */
public record GoogleSignInRequest(
        @NotBlank(message = "Thiếu token Google") String idToken) {
}
