package com.ut.edu.backend.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vector verified independently via `openssl dgst -sha256 -hmac` against the
 * exact "{timestamp}.{rawBody}" string before being hardcoded here - not
 * hand-derived. See SePaySignatureService's javadoc for the signing scheme.
 */
class SePaySignatureServiceTest {

    private static final String SECRET = "test-secret";
    private static final String TIMESTAMP = "1730000000";
    private static final String RAW_BODY = "{\"id\":1,\"content\":\"DH1 test\"}";
    private static final String VALID_SIGNATURE =
            "sha256=89aed23f314156642dc9276bbe16702271f4c8286267138607018435629c9a86";

    private SePaySignatureService service;

    @BeforeEach
    void setUp() {
        service = new SePaySignatureService();
        ReflectionTestUtils.setField(service, "webhookSecret", SECRET);
    }

    @Test
    void verifyWebhookSignature_validSignature_returnsTrue() {
        assertThat(service.verifyWebhookSignature(RAW_BODY, TIMESTAMP, VALID_SIGNATURE)).isTrue();
    }

    @Test
    void verifyWebhookSignature_tamperedBody_returnsFalse() {
        assertThat(service.verifyWebhookSignature(RAW_BODY + " ", TIMESTAMP, VALID_SIGNATURE)).isFalse();
    }

    @Test
    void verifyWebhookSignature_wrongTimestamp_returnsFalse() {
        assertThat(service.verifyWebhookSignature(RAW_BODY, "1730000001", VALID_SIGNATURE)).isFalse();
    }

    @Test
    void verifyWebhookSignature_tamperedSignature_returnsFalse() {
        assertThat(service.verifyWebhookSignature(RAW_BODY, TIMESTAMP, "sha256=0000")).isFalse();
    }

    @Test
    void verifyWebhookSignature_missingSecret_returnsFalse() {
        ReflectionTestUtils.setField(service, "webhookSecret", "");
        assertThat(service.verifyWebhookSignature(RAW_BODY, TIMESTAMP, VALID_SIGNATURE)).isFalse();
    }

    @Test
    void verifyWebhookSignature_nullHeaders_returnsFalse() {
        assertThat(service.verifyWebhookSignature(RAW_BODY, null, VALID_SIGNATURE)).isFalse();
        assertThat(service.verifyWebhookSignature(RAW_BODY, TIMESTAMP, null)).isFalse();
    }

    @Test
    void verifyWebhookSignature_differentSecret_producesDifferentResult() {
        SePaySignatureService wrongSecretService = new SePaySignatureService();
        ReflectionTestUtils.setField(wrongSecretService, "webhookSecret", "a-completely-different-secret");

        assertThat(wrongSecretService.verifyWebhookSignature(RAW_BODY, TIMESTAMP, VALID_SIGNATURE)).isFalse();
    }
}
