package com.ut.edu.backend.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vectors verified independently via Python hmac/hashlib against the real
 * public sandbox secret key before being hardcoded here - not hand-derived.
 */
class MomoSignatureServiceTest {

    private static final String PARTNER_CODE = "MOMO";
    private static final String ACCESS_KEY = "F8BBA842ECF85";
    private static final String SECRET_KEY = "K951B6PE1waDMi640xX08PD3vg6EkVlz";

    private MomoSignatureService service;

    @BeforeEach
    void setUp() {
        service = new MomoSignatureService();
        ReflectionTestUtils.setField(service, "partnerCode", PARTNER_CODE);
        ReflectionTestUtils.setField(service, "accessKey", ACCESS_KEY);
        ReflectionTestUtils.setField(service, "secretKey", SECRET_KEY);
    }

    @Test
    void signCreatePayment_matchesVerifiedVector() {
        String signature = service.signCreatePayment(
                "50000", "", "https://example.com/payments/webhook/momo", "ORD-TEST-001",
                "Thanh toan don hang ORD-TEST-001", "https://example.com/payment/success",
                "11111111-1111-1111-1111-111111111111");

        assertThat(signature).isEqualTo("f78fe3aa46fd2693bf00501b937679d40dc7fe9998434b7e8665aa5ad42420f8");
    }

    private Map<String, Object> validIpnPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderType", "momo_wallet");
        payload.put("amount", 50000);
        payload.put("partnerCode", PARTNER_CODE);
        payload.put("orderId", "ORD-TEST-001");
        payload.put("extraData", "");
        payload.put("transId", 4088878653L);
        payload.put("responseTime", 1721720663942L);
        payload.put("resultCode", 0);
        payload.put("message", "Successful.");
        payload.put("payType", "qr");
        payload.put("requestId", "11111111-1111-1111-1111-111111111111");
        payload.put("orderInfo", "Thanh toan don hang ORD-TEST-001");
        payload.put("signature", "24eea3d0ad89d4d814fd2ac1afeb0f1ae161285edd71ca6ffc69e8397025dc24");
        return payload;
    }

    @Test
    void verifyIpnSignature_validPayload_returnsTrue() {
        assertThat(service.verifyIpnSignature(validIpnPayload())).isTrue();
    }

    @Test
    void verifyIpnSignature_tamperedAmount_returnsFalse() {
        Map<String, Object> payload = validIpnPayload();
        payload.put("amount", 99999);

        assertThat(service.verifyIpnSignature(payload)).isFalse();
    }

    @Test
    void verifyIpnSignature_tamperedSignature_returnsFalse() {
        Map<String, Object> payload = validIpnPayload();
        payload.put("signature", "0000000000000000000000000000000000000000000000000000000000000");

        assertThat(service.verifyIpnSignature(payload)).isFalse();
    }

    @Test
    void verifyIpnSignature_missingSignatureField_returnsFalse() {
        Map<String, Object> payload = validIpnPayload();
        payload.remove("signature");

        assertThat(service.verifyIpnSignature(payload)).isFalse();
    }

    @Test
    void verifyIpnSignature_isIndependentOfMapIterationOrder() {
        // HashMap has no guaranteed iteration order - if the raw string were built
        // by iterating the map instead of a fixed field order, this would flake.
        Map<String, Object> hashMapPayload = new HashMap<>(validIpnPayload());
        Map<String, Object> treeMapPayload = new TreeMap<>(validIpnPayload());

        assertThat(service.verifyIpnSignature(hashMapPayload)).isTrue();
        assertThat(service.verifyIpnSignature(treeMapPayload)).isTrue();
    }

    @Test
    void signCreatePayment_differentSecretKey_producesDifferentSignature() {
        String signature = service.signCreatePayment(
                "50000", "", "https://example.com/payments/webhook/momo", "ORD-TEST-001",
                "Thanh toan don hang ORD-TEST-001", "https://example.com/payment/success",
                "11111111-1111-1111-1111-111111111111");

        MomoSignatureService wrongKeyService = new MomoSignatureService();
        ReflectionTestUtils.setField(wrongKeyService, "partnerCode", PARTNER_CODE);
        ReflectionTestUtils.setField(wrongKeyService, "accessKey", ACCESS_KEY);
        ReflectionTestUtils.setField(wrongKeyService, "secretKey", "a-completely-different-secret");

        String differentSignature = wrongKeyService.signCreatePayment(
                "50000", "", "https://example.com/payments/webhook/momo", "ORD-TEST-001",
                "Thanh toan don hang ORD-TEST-001", "https://example.com/payment/success",
                "11111111-1111-1111-1111-111111111111");

        assertThat(differentSignature).isNotEqualTo(signature);
    }
}
