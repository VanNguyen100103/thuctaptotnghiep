package com.ut.edu.backend.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * HMAC-SHA256 verification for SePay's webhook - the method SePay's own
 * dashboard recommends over a bare API Key. SePay sends
 * "X-SePay-Signature: sha256={hex}" + "X-SePay-Timestamp: {unix}", computed
 * over "{timestamp}.{body}". Per SePay's own sample code (shown in the
 * dashboard when picking this method), "{body}" is JSON.stringify(req.body)
 * - i.e. their side signs the body AFTER it's already been parsed by their
 * framework's JSON middleware, not the literal raw HTTP bytes - so
 * PaymentController passes this the parsed payload re-serialized with the
 * app's own ObjectMapper, not a raw request body string.
 */
@Component
public class SePaySignatureService {

    @Value("${sepay.webhook-secret}")
    private String webhookSecret;

    public boolean verifyWebhookSignature(String canonicalJsonBody, String timestamp, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()
                || canonicalJsonBody == null || timestamp == null || signatureHeader == null) {
            return false;
        }
        String expected = "sha256=" + hmacSha256Hex(timestamp + "." + canonicalJsonBody);
        return expected.equals(signatureHeader);
    }

    private String hmacSha256Hex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new SePayApiException("Failed to compute SePay webhook signature", e);
        }
    }
}
