package com.ut.edu.backend.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Verifies the ID token Google hands the browser during sign-in.
 *
 * The flow is the browser-side one (Google Identity Services): the user picks
 * an account in Google's own popup, Google returns a signed ID token to the
 * page, and the page posts it here. Nothing about the user is trusted from
 * the page - only what survives verifying that signature.
 *
 * The redirect-based server flow was not used because this app is an SPA on
 * one origin talking to an API on another, with no server-rendered session to
 * hang an OAuth round trip off. Posting an ID token needs no callback URL, no
 * session cookie, and no third origin.
 *
 * Verification is Spring's NimbusJwtDecoder rather than hand-rolled: it
 * fetches Google's JWKS, follows key rotation, and checks issuer and expiry.
 * Audience it does not check, so that is done here - without it, an ID token
 * minted for any other Google app would be accepted by this one.
 */
@Service
@Slf4j
public class GoogleSignInService {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    /** The OAuth Client ID from Google Cloud Console. Also the audience every ID token for this app carries. */
    @Value("${google.client-id:}")
    private String clientId;

    /**
     * Built on first use, not at startup: constructing it fetches Google's
     * OIDC metadata over the network, and a deployment with no Google sign-in
     * configured should not pay for that - nor fail to boot if Google is
     * briefly unreachable.
     */
    private volatile JwtDecoder decoder;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    /**
     * @return the verified email address, which Google has confirmed belongs
     *         to whoever signed in
     * @throws GoogleSignInException if the token is not a valid, current,
     *         verified-email identity issued for this app
     */
    public String verifyEmail(String idToken) {
        if (!isConfigured()) {
            throw new GoogleSignInException("Đăng nhập Google chưa được cấu hình");
        }

        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            // Signature, issuer or expiry - all of them mean the same thing to
            // the caller, and saying which would help someone probing.
            log.warn("Rejected a Google ID token: {}", e.getMessage());
            throw new GoogleSignInException("Token Google không hợp lệ", e);
        }

        if (!jwt.getAudience().contains(clientId)) {
            log.warn("Google ID token was issued for another app: aud={}", jwt.getAudience());
            throw new GoogleSignInException("Token Google không dành cho ứng dụng này");
        }

        // An unverified address proves nothing: anyone can put someone else's
        // email on a Google account they have not confirmed.
        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            throw new GoogleSignInException("Email Google này chưa được xác minh");
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new GoogleSignInException("Token Google không chứa email");
        }
        return email;
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (decoder == null) {
                try {
                    decoder = JwtDecoders.fromIssuerLocation(GOOGLE_ISSUER);
                } catch (Exception e) {
                    throw new GoogleSignInException("Không kết nối được tới Google để xác minh token", e);
                }
            }
            return decoder;
        }
    }
}
