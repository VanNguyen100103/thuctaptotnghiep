package com.ut.edu.backend.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * HMAC-SHA256 signing/verification for MoMo's payment API, per
 * developers.momo.vn. Pure - no HTTP, no Spring context needed beyond the
 * three injected config values, so it's cheap to unit test exhaustively.
 * Field order in both raw strings is alphabetical, per MoMo's docs - do not
 * reorder without checking the docs again, the signature is order-sensitive.
 */
@Component
public class MomoSignatureService {

    @Value("${momo.partner-code}")
    private String partnerCode;

    @Value("${momo.access-key}")
    private String accessKey;

    @Value("${momo.secret-key}")
    private String secretKey;

    /** Signature for POST /v2/gateway/api/create (requestType always "captureWallet" in this app). */
    public String signCreatePayment(String amount, String extraData, String ipnUrl, String orderId,
                                     String orderInfo, String redirectUrl, String requestId) {
        String raw = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=captureWallet";
        return hmacSha256Hex(raw);
    }

    /** Verifies an inbound IPN's signature by recomputing it from the payload fields. */
    public boolean verifyIpnSignature(Map<String, Object> payload) {
        String raw = "accessKey=" + accessKey
                + "&amount=" + String.valueOf(payload.get("amount"))
                + "&extraData=" + String.valueOf(payload.get("extraData"))
                + "&message=" + String.valueOf(payload.get("message"))
                + "&orderId=" + String.valueOf(payload.get("orderId"))
                + "&orderInfo=" + String.valueOf(payload.get("orderInfo"))
                + "&orderType=" + String.valueOf(payload.get("orderType"))
                + "&partnerCode=" + String.valueOf(payload.get("partnerCode"))
                + "&payType=" + String.valueOf(payload.get("payType"))
                + "&requestId=" + String.valueOf(payload.get("requestId"))
                + "&responseTime=" + String.valueOf(payload.get("responseTime"))
                + "&resultCode=" + String.valueOf(payload.get("resultCode"))
                + "&transId=" + String.valueOf(payload.get("transId"));
        String expected = hmacSha256Hex(raw);
        return expected.equals(payload.get("signature"));
    }

    private String hmacSha256Hex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new MomoApiException("Failed to compute MoMo signature", e);
        }
    }
}
