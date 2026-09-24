package com.ut.edu.backend.automation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vector produced independently with `openssl dgst -sha256 -hmac test-secret`
 * over the exact "{timestamp}.{body}" string, not derived from this code.
 *
 * It is pinned here because the other half of this contract lives in an n8n
 * workflow written in JavaScript, in another repository's worth of JSON. If
 * someone changes the construction on this side, the shop finds out when its
 * alerts stop arriving; this test finds out immediately.
 */
class AutomationSignatureServiceTest {

    private static final String SECRET = "test-secret";
    private static final String TIMESTAMP = "1730000000";
    private static final String BODY = "{\"event\":\"sale.completed\",\"storeId\":42}";
    private static final String EXPECTED =
            "sha256=6f62249b723acecf2ebe3e9489db7582286c770f6498a9fc45592e9c0ad3066c";

    private AutomationSignatureService service;

    @BeforeEach
    void setUp() {
        service = new AutomationSignatureService();
        ReflectionTestUtils.setField(service, "webhookSecret", SECRET);
    }

    @Test
    void sign_matchesTheAgreedConstruction() {
        assertThat(service.sign(TIMESTAMP, BODY)).isEqualTo(EXPECTED);
    }

    @Test
    void sign_changesWhenTheBodyIsTamperedWith() {
        assertThat(service.sign(TIMESTAMP, BODY + " ")).isNotEqualTo(EXPECTED);
    }

    /** The timestamp is signed, not merely sent - this is what stops a replay. */
    @Test
    void sign_changesWhenTheTimestampChanges() {
        assertThat(service.sign("1730000001", BODY)).isNotEqualTo(EXPECTED);
    }

    @Test
    void isConfigured_falseWhenNoSecretIsSet() {
        ReflectionTestUtils.setField(service, "webhookSecret", "  ");
        assertThat(service.isConfigured()).isFalse();
    }
}
