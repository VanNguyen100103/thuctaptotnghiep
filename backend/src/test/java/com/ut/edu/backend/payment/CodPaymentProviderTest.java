package com.ut.edu.backend.payment;

import com.ut.edu.backend.order.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodPaymentProviderTest {

    private CodPaymentProvider provider;
    private Order order;

    @BeforeEach
    void setUp() {
        provider = new CodPaymentProvider();
        order = Order.builder().id(1L).orderNumber("ORD-1").total(new BigDecimal("250000.00")).build();
    }

    @Test
    void getMethod_isCashOnDelivery() {
        assertThat(provider.getMethod()).isEqualTo(PaymentMethod.CASH_ON_DELIVERY);
    }

    @Test
    void confirmsImmediately_isTrue() {
        assertThat(provider.confirmsImmediately()).isTrue();
    }

    @Test
    void createPayment_returnsSuccessUrlAsRedirectUrl_withNoGatewayReferenceOrRawResponse() {
        PaymentInitiationResult result = provider.createPayment(order, "https://x/success", "https://x/cancel");

        assertThat(result.redirectUrl()).isEqualTo("https://x/success");
        assertThat(result.gatewayReferenceId()).isNull();
        assertThat(result.rawResponseJson()).isNull();
    }

    @Test
    void refund_alwaysThrowsRefundNotSupported() {
        Payment payment = Payment.builder().build();

        assertThatThrownBy(() -> provider.refund(payment, new BigDecimal("10000")))
                .isInstanceOf(RefundNotSupportedException.class);
    }
}
