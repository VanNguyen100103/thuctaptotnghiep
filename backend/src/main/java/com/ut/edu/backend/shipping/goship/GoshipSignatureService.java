package com.ut.edu.backend.shipping.goship;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Verifies the x-goship-hmac-sha256 header Goship sends with every webhook,
 * keyed on the API client secret.
 *
 * Goship's own reference implementations disagree with each other, so this
 * accepts either reading rather than betting on one. Their PHP sample signs
 * with hash_hmac(..., true) and base64-encodes the raw digest; their Node
 * sample base64-encodes the digest's *hex string* instead, which is a
 * different value for the same input. Both are documented as correct.
 *
 * The signed text is ambiguous in the same way: both samples sign a
 * re-serialized copy of the parsed body (json_encode / JSON.stringify), but
 * PHP and Jackson do not escape identically - PHP escapes forward slashes
 * and non-ASCII by default - so a re-serialized string from here may differ
 * from theirs byte for byte. The raw body as received is therefore checked
 * as well.
 *
 * Four candidates is inelegant, and deliberate: the failure mode of
 * guessing wrong is a webhook silently rejected with a 401 and shipment
 * statuses that never update, which is exactly the bug this codebase
 * already worked through once with SePay. Every candidate is still a real
 * HMAC keyed on a secret only Goship knows, so accepting several does not
 * weaken the check.
 */
@Component
@Slf4j
public class GoshipSignatureService {

    @Value("${goship.client-secret:}")
    private String clientSecret;

    public boolean isConfigured() {
        return clientSecret != null && !clientSecret.isBlank();
    }

    /**
     * @param rawBody       the request body exactly as received
     * @param canonicalJson the same body parsed and written back out by this app's ObjectMapper
     */
    public boolean verify(String rawBody, String canonicalJson, String signatureHeader) {
        if (!isConfigured() || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        return matchesAnyEncoding(rawBody, signatureHeader) || matchesAnyEncoding(canonicalJson, signatureHeader);
    }

    private boolean matchesAnyEncoding(String signed, String presented) {
        if (signed == null) {
            return false;
        }
        byte[] digest = hmac(signed);
        String rawDigestBase64 = Base64.getEncoder().encodeToString(digest);
        String hexDigestBase64 = Base64.getEncoder()
                .encodeToString(HexFormat.of().formatHex(digest).getBytes(StandardCharsets.UTF_8));
        return constantTimeEquals(rawDigestBase64, presented) || constantTimeEquals(hexDigestBase64, presented);
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new GoshipApiException("Failed to compute Goship webhook signature", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
