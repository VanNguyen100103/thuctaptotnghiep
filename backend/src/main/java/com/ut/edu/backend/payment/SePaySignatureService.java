package com.ut.edu.backend.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Authenticates SePay's webhook, by either of the two methods a deployment
 * can be set up for.
 *
 * API Key is the one SePay's dashboard actually offers (docs.sepay.vn lists
 * "Không cần chứng thực", API Key and IP whitelisting): it sends
 * "Authorization: Apikey {key}" and nothing else. HMAC-SHA256 is kept
 * because this app shipped with it and is strictly stronger where a
 * deployment does send it - but a dashboard set to API Key sends no
 * signature headers at all, so requiring HMAC there rejects every webhook
 * and the shop just sees payments never confirm.
 *
 * For the HMAC method SePay sends
 * "X-SePay-Signature: sha256={hex}" + "X-SePay-Timestamp: {unix}", computed
 * over "{timestamp}.{raw body}". The raw part matters: developer.sepay.vn
 * warns in so many words that a body parsed by JSON middleware and then
 * serialized back will not produce a matching signature, so PaymentController
 * hands this the untouched request body and only parses it afterwards.
 */
@Component
public class SePaySignatureService {

    @Value("${sepay.webhook-secret}")
    private String webhookSecret;

    @Value("${sepay.webhook-api-key:}")
    private String webhookApiKey;

    /** SePay's own header format. A key pasted in bare is accepted too, since that is an easy thing to get wrong. */
    private static final String API_KEY_PREFIX = "Apikey ";

    /**
     * Whether an HMAC secret is configured at all. Without one nothing can be
     * accepted by that method, which looks identical to a wrong signature
     * from the outside - PaymentController uses this to say which it was.
     */
    public boolean isConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    public boolean isApiKeyConfigured() {
        return webhookApiKey != null && !webhookApiKey.isBlank();
    }

    /**
     * Checks the "Authorization: Apikey {key}" header SePay sends when the
     * webhook is configured for API Key auth. Constant-time comparison, so
     * the endpoint cannot be used to guess the key one character at a time.
     */
    public boolean verifyApiKey(String authorizationHeader) {
        if (!isApiKeyConfigured() || authorizationHeader == null) {
            return false;
        }
        String presented = authorizationHeader.startsWith(API_KEY_PREFIX)
                ? authorizationHeader.substring(API_KEY_PREFIX.length()).trim()
                : authorizationHeader.trim();
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                webhookApiKey.getBytes(StandardCharsets.UTF_8));
    }

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
