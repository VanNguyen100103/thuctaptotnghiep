package com.ut.edu.backend.payment;

import com.ut.edu.backend.order.Order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * MoMo e-wallet checkout. Confirmation is IPN-only (see PaymentController's
 * /payments/webhook/momo) - createPayment only gets the browser redirected
 * to MoMo's payUrl, nothing is confirmed synchronously the way PayPal's
 * /execute does. No dedicated REST client class (unlike PayPalRestClient) -
 * MoMo is exactly one HTTP call with no OAuth dance, so a separate client
 * abstraction would be ceremony, not reuse.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MomoPaymentProvider implements PaymentProvider {

    private final RestTemplate restTemplate;
    private final MomoSignatureService momoSignatureService;
    private final ObjectMapper objectMapper;

    @Value("${momo.partner-code}")
    private String partnerCode;

    @Value("${momo.endpoint}")
    private String endpoint;

    @Value("${app.backend.url}")
    private String backendUrl;

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.MOMO;
    }

    @Override
    public PaymentInitiationResult createPayment(Order order, String successUrl, String cancelUrl) {
        String orderId = order.getOrderNumber();
        String requestId = UUID.randomUUID().toString();
        String amount = order.getTotal().setScale(0, RoundingMode.HALF_UP).toBigInteger().toString();
        String orderInfo = "Thanh toan don hang " + orderId;
        String ipnUrl = backendUrl + "/payments/webhook/momo";
        String extraData = "";

        String signature = momoSignatureService.signCreatePayment(
                amount, extraData, ipnUrl, orderId, orderInfo, successUrl, requestId);

        Map<String, Object> requestBody = Map.ofEntries(
                Map.entry("partnerCode", partnerCode),
                Map.entry("requestType", "captureWallet"),
                Map.entry("ipnUrl", ipnUrl),
                Map.entry("redirectUrl", successUrl),
                Map.entry("orderId", orderId),
                Map.entry("amount", amount),
                Map.entry("orderInfo", orderInfo),
                Map.entry("requestId", requestId),
                Map.entry("extraData", extraData),
                Map.entry("signature", signature),
                Map.entry("lang", "vi"));

        Map<String, Object> response = post(requestBody);

        Object resultCode = response.get("resultCode");
        if (!(resultCode instanceof Number number) || number.intValue() != 0) {
            throw new MomoApiException("MoMo create-payment failed: " + response.get("message"));
        }

        Object payUrl = response.get("payUrl");
        if (payUrl == null) {
            throw new MomoApiException("MoMo response had no payUrl: " + response);
        }

        try {
            return new PaymentInitiationResult(payUrl.toString(), null, objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            throw new MomoApiException("Failed to serialize MoMo response", e);
        }
    }

    @Override
    public PaymentRefundResult refund(Payment payment, BigDecimal amount) {
        throw new RefundNotSupportedException(
                "MoMo refunds aren't integrated yet (POST /v2/gateway/api/refund is a separate, unresearched contract)");
    }

    private Map<String, Object> post(Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    endpoint + "/v2/gateway/api/create", new HttpEntity<>(requestBody, headers), Map.class);
            if (response == null) {
                throw new MomoApiException("MoMo returned an empty response");
            }
            return response;
        } catch (RestClientException e) {
            throw new MomoApiException("MoMo API call failed", e);
        }
    }
}
