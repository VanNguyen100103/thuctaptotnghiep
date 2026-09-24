package com.ut.edu.backend.automation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Signs what this app posts to n8n, so a workflow can tell a real event from
 * anyone who has guessed the webhook URL.
 *
 * n8n webhook URLs are unauthenticated by default and the path is the only
 * secret in them - one leaked into a screenshot, a browser history or a CI
 * log is enough for a stranger to tell a shop it has an order it does not
 * have. The signature is the same construction this codebase already
 * verifies incoming SePay webhooks with (HMAC-SHA256 over
 * "{timestamp}.{raw body}", hex, prefixed "sha256="), for the plain reason
 * that a reader who has understood one has understood both.
 *
 * The timestamp is inside the signed material, not merely alongside it, so a
 * captured request cannot be replayed later with its clock rewritten - the
 * receiving workflow rejects anything older than its own tolerance.
 */
@Component
public class AutomationSignatureService {

    @Value("${automation.webhook-secret:}")
    private String webhookSecret;

    /** Without a secret nothing can be signed, and the dispatcher refuses to send unsigned. */
    public boolean isConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    /**
     * The X-Tryum-Signature value for this body at this timestamp.
     *
     * @param epochSeconds the value sent as X-Tryum-Timestamp - it must be the
     *                     same string on the wire, since it is signed as text
     * @param body         the exact bytes that will be posted; re-serializing
     *                     the JSON before sending would invalidate this
     */
    public String sign(String epochSeconds, String body) {
        return "sha256=" + hmacSha256Hex(epochSeconds + "." + body);
    }

    private String hmacSha256Hex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute automation webhook signature", e);
        }
    }
}
