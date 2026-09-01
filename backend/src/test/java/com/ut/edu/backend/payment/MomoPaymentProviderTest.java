package com.ut.edu.backend.payment;

import com.ut.edu.backend.order.Order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MomoPaymentProviderTest {

    @Mock private RestTemplate restTemplate;
    @Mock private MomoSignatureService momoSignatureService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MomoPaymentProvider provider;
    private Order order;

    @BeforeEach
    void setUp() {
        provider = new MomoPaymentProvider(restTemplate, momoSignatureService, objectMapper);
        ReflectionTestUtils.setField(provider, "partnerCode", "MOMO");
        ReflectionTestUtils.setField(provider, "endpoint", "https://test-payment.momo.vn");
        ReflectionTestUtils.setField(provider, "backendUrl", "https://backend.example.com/api");

        order = Order.builder().id(1L).orderNumber("ORD-1").total(new BigDecimal("500000.00")).build();
    }

    @Test
    void getMethod_isMomo() {
        assertThat(provider.getMethod()).isEqualTo(PaymentMethod.MOMO);
    }

    @SuppressWarnings("unchecked")
    @Test
    void createPayment_sendsCorrectlyShapedRequestAndReturnsPayUrl() {
        when(momoSignatureService.signCreatePayment(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("fake-signature");
        when(restTemplate.postForObject(eq("https://test-payment.momo.vn/v2/gateway/api/create"), any(), eq(Map.class)))
                .thenReturn(Map.of("resultCode", 0, "payUrl", "https://pay.momo.vn/xyz", "orderId", "ORD-1"));

        PaymentInitiationResult result = provider.createPayment(order, "https://x/success", "https://x/cancel");

        assertThat(result.redirectUrl()).isEqualTo("https://pay.momo.vn/xyz");
        assertThat(result.gatewayReferenceId()).isNull();

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate).postForObject(anyString(), captor.capture(), eq(Map.class));
        Map<String, Object> body = captor.getValue().getBody();

        assertThat(body).containsEntry("partnerCode", "MOMO");
        assertThat(body).containsEntry("requestType", "captureWallet");
        assertThat(body).containsEntry("orderId", "ORD-1");
        assertThat(body).containsEntry("amount", "500000"); // integer VND, no decimals/grouping
        assertThat(body).containsEntry("ipnUrl", "https://backend.example.com/api/payments/webhook/momo");
        assertThat(body).containsEntry("redirectUrl", "https://x/success");
        assertThat(body).containsEntry("signature", "fake-signature");
        assertThat(body.get("requestId")).isNotNull();
    }

    @Test
    void createPayment_nonZeroResultCode_throwsMomoApiException() {
        when(momoSignatureService.signCreatePayment(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("fake-signature");
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("resultCode", 99, "message", "Invalid amount"));

        assertThatThrownBy(() -> provider.createPayment(order, "https://x/success", "https://x/cancel"))
                .isInstanceOf(MomoApiException.class)
                .hasMessageContaining("Invalid amount");
    }

    @Test
    void createPayment_restClientException_wrappedInMomoApiException() {
        when(momoSignatureService.signCreatePayment(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("fake-signature");
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertThatThrownBy(() -> provider.createPayment(order, "https://x/success", "https://x/cancel"))
                .isInstanceOf(MomoApiException.class);
    }

    @Test
    void refund_alwaysThrowsRefundNotSupported() {
        Payment payment = Payment.builder().build();

        assertThatThrownBy(() -> provider.refund(payment, new BigDecimal("10000")))
                .isInstanceOf(RefundNotSupportedException.class);

        verifyNoInteractions(restTemplate);
    }
}
