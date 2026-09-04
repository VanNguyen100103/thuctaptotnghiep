package com.ut.edu.backend.payment;

import com.ut.edu.backend.order.Order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SePayPaymentProviderTest {

    private SePayPaymentProvider provider;
    private Order order;

    @BeforeEach
    void setUp() {
        provider = new SePayPaymentProvider();
        ReflectionTestUtils.setField(provider, "accountNumber", "5811652764");
        ReflectionTestUtils.setField(provider, "bankCode", "BIDV");
        ReflectionTestUtils.setField(provider, "accountName", "");

        order = Order.builder().id(482L).orderNumber("ORD-1").total(new BigDecimal("150000.00")).build();
    }

    @Test
    void getMethod_isBankTransfer() {
        assertThat(provider.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void confirmsImmediately_isFalse() {
        // Still needs the webhook to actually confirm - unlike COD.
        assertThat(provider.confirmsImmediately()).isFalse();
    }

    @Test
    void createPayment_buildsVietQrUrlWithOrderIdAsContent() {
        PaymentInitiationResult result = provider.createPayment(order, "https://x/success", "https://x/cancel");

        Map<String, String> params = queryParams(result.redirectUrl());
        assertThat(result.redirectUrl()).startsWith("https://vietqr.app/img?");
        assertThat(params).containsEntry("acc", "5811652764");
        assertThat(params).containsEntry("bank", "BIDV");
        assertThat(params).containsEntry("amount", "150000"); // integer VND, no decimals
        assertThat(params).containsEntry("des", "DH482");
        assertThat(params).containsEntry("template", "compact");

        // content, not the QR image, is what the SePay webhook parses back out
        // of the transfer's free-text description - see PaymentController.
        assertThat(result.gatewayReferenceId()).isEqualTo("DH482");
    }

    @Test
    void createPayment_missingAccountNumber_throwsSePayApiException() {
        ReflectionTestUtils.setField(provider, "accountNumber", "");

        assertThatThrownBy(() -> provider.createPayment(order, "https://x/success", "https://x/cancel"))
                .isInstanceOf(SePayApiException.class);
    }

    @Test
    void createPayment_missingBankCode_throwsSePayApiException() {
        ReflectionTestUtils.setField(provider, "bankCode", null);

        assertThatThrownBy(() -> provider.createPayment(order, "https://x/success", "https://x/cancel"))
                .isInstanceOf(SePayApiException.class);
    }

    @Test
    void refund_alwaysThrowsRefundNotSupported() {
        Payment payment = Payment.builder().build();

        assertThatThrownBy(() -> provider.refund(payment, new BigDecimal("10000")))
                .isInstanceOf(RefundNotSupportedException.class);
    }

    private static Map<String, String> queryParams(String url) {
        return UriComponentsBuilder.fromUri(URI.create(url)).build().getQueryParams()
                .toSingleValueMap();
    }
}
