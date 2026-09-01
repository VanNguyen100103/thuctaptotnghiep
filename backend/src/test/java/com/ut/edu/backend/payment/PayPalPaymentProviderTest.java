package com.ut.edu.backend.payment;

import com.ut.edu.backend.order.Order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.base.rest.PayPalRESTException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayPalPaymentProviderTest {

    @Mock private PayPalService payPalService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PayPalPaymentProvider provider;

    private Order order;

    @BeforeEach
    void setUp() {
        provider = new PayPalPaymentProvider(payPalService, objectMapper);
        order = Order.builder().id(1L).orderNumber("ORD-1").total(new BigDecimal("100000.00")).build();
    }

    @Test
    void getMethod_isPaypal() {
        assertThat(provider.getMethod()).isEqualTo(PaymentMethod.PAYPAL);
    }

    @Test
    void createPayment_delegatesToPayPalServiceAndReturnsApprovalUrl() throws PayPalRESTException {
        com.paypal.api.payments.Payment sdkPayment = new com.paypal.api.payments.Payment();
        sdkPayment.setId("PAY-123");
        when(payPalService.createPayment(order, "https://x/success", "https://x/cancel")).thenReturn(sdkPayment);
        when(payPalService.getApprovalUrl(sdkPayment)).thenReturn("https://approve.example");

        PaymentInitiationResult result = provider.createPayment(order, "https://x/success", "https://x/cancel");

        assertThat(result.redirectUrl()).isEqualTo("https://approve.example");
        assertThat(result.gatewayReferenceId()).isEqualTo("PAY-123");
        assertThat(result.rawResponseJson()).contains("PAY-123");
    }

    @Test
    void createPayment_noApprovalUrl_throwsPayPalApiException() throws PayPalRESTException {
        com.paypal.api.payments.Payment sdkPayment = new com.paypal.api.payments.Payment();
        sdkPayment.setId("PAY-123");
        when(payPalService.createPayment(any(), anyString(), anyString())).thenReturn(sdkPayment);
        when(payPalService.getApprovalUrl(sdkPayment)).thenReturn(null);

        assertThatThrownBy(() -> provider.createPayment(order, "https://x/success", "https://x/cancel"))
                .isInstanceOf(PayPalApiException.class);
    }

    @Test
    void createPayment_payPalRestException_translatedToPayPalApiException() throws PayPalRESTException {
        when(payPalService.createPayment(any(), anyString(), anyString()))
                .thenThrow(new PayPalRESTException("boom"));

        assertThatThrownBy(() -> provider.createPayment(order, "https://x/success", "https://x/cancel"))
                .isInstanceOf(PayPalApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void refund_delegatesToPayPalServiceUsingTransactionId() throws PayPalRESTException {
        Payment payment = Payment.builder().transactionId("SALE-1").build();
        com.paypal.api.payments.DetailedRefund refund = new com.paypal.api.payments.DetailedRefund();
        refund.setId("REFUND-1");
        when(payPalService.refundPayment("SALE-1", new BigDecimal("50.00"))).thenReturn(refund);

        PaymentRefundResult result = provider.refund(payment, new BigDecimal("50.00"));

        assertThat(result.refundReference()).isEqualTo("REFUND-1");
    }

    @Test
    void refund_payPalRestException_translatedToPayPalApiException() throws PayPalRESTException {
        Payment payment = Payment.builder().transactionId("SALE-1").build();
        when(payPalService.refundPayment(eq("SALE-1"), any())).thenThrow(new PayPalRESTException("refund failed"));

        assertThatThrownBy(() -> provider.refund(payment, new BigDecimal("50.00")))
                .isInstanceOf(PayPalApiException.class)
                .hasMessageContaining("refund failed");
    }
}
