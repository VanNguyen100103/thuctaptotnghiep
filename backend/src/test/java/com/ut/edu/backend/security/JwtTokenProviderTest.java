package com.ut.edu.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET =
            "unit-test-secret-key-that-is-long-enough-for-hs512-0123456789abcdef";

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = newProvider(3_600_000L); // 1h expiry
    }

    private JwtTokenProvider newProvider(long expirationMs) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", expirationMs);
        ReflectionTestUtils.setField(provider, "jwtRefreshExpirationMs", 7 * 24 * 3_600_000L);
        return provider;
    }

    private Authentication authenticationFor(String username) {
        return authenticationFor(username, null, null);
    }

    private Authentication authenticationFor(String username, Long storeId, String storeRole) {
        UserPrincipal principal = new UserPrincipal(
                42L, username, username + "@example.com", "secret",
                List.of(new SimpleGrantedAuthority("ROLE_USER")), true, true,
                storeId, storeRole);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void generatedToken_isValidAndCarriesClaims() {
        String token = tokenProvider.generateToken(authenticationFor("alice"));

        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo("alice");
        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
        assertThat(tokenProvider.isTokenExpired(token)).isFalse();
    }

    @Test
    void storeStaffToken_carriesTenantClaim() {
        String token = tokenProvider.generateToken(authenticationFor("owner", 7L, "OWNER"));

        assertThat(tokenProvider.getStoreIdFromToken(token)).isEqualTo(7L);
    }

    @Test
    void customerToken_hasNoTenantClaim() {
        String token = tokenProvider.generateToken(authenticationFor("alice"));

        assertThat(tokenProvider.getStoreIdFromToken(token)).isNull();
    }

    @Test
    void refreshToken_isValid() {
        String token = tokenProvider.generateRefreshToken(authenticationFor("alice"));

        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo("alice");
    }

    @Test
    void tamperedToken_isRejected() {
        String token = tokenProvider.generateToken(authenticationFor("alice"));
        // flip a character in the payload section
        String tampered = token.substring(0, token.length() - 5) + "aaaaa";

        assertThat(tokenProvider.validateToken(tampered)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentKey_isRejected() {
        JwtTokenProvider other = new JwtTokenProvider();
        ReflectionTestUtils.setField(other, "jwtSecret",
                "another-secret-key-that-is-also-long-enough-for-hs512-9876543210fedcba");
        ReflectionTestUtils.setField(other, "jwtExpirationMs", 3_600_000L);
        ReflectionTestUtils.setField(other, "jwtRefreshExpirationMs", 3_600_000L);
        String foreignToken = other.generateToken(authenticationFor("mallory"));

        assertThat(tokenProvider.validateToken(foreignToken)).isFalse();
    }

    @Test
    void expiredToken_isRejected() {
        JwtTokenProvider shortLived = newProvider(-1_000L); // already expired at issue time
        String token = shortLived.generateToken(authenticationFor("alice"));

        assertThat(shortLived.validateToken(token)).isFalse();
        assertThat(shortLived.isTokenExpired(token)).isTrue();
    }

    @Test
    void garbageInput_isRejected() {
        assertThat(tokenProvider.validateToken("not-a-jwt")).isFalse();
        assertThat(tokenProvider.validateToken("")).isFalse();
        assertThat(tokenProvider.isTokenExpired("not-a-jwt")).isTrue();
    }
}
