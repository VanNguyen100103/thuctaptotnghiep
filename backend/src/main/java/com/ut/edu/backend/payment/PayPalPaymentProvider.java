package com.ut.edu.backend.payment;

import com.ut.edu.backend.order.Order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.base.rest.PayPalRESTException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Thin Strategy-pattern adapter around the existing, untouched PayPalService.
 * No PayPal SDK calls are changed here - this only translates its checked
 * PayPalRESTException into the unchecked PayPalApiException and adapts the
 * shapes to PaymentProvider's generic result records.
 */
@Component
@RequiredArgsConstructor
public class PayPalPaymentProvider implements PaymentProvider {

    private final PayPalService payPalService;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.PAYPAL;
    }

    @Override
    public PaymentInitiationResult createPayment(Order order, String successUrl, String cancelUrl) {
        try {
            com.paypal.api.payments.Payment paypalPayment = payPalService.createPayment(order, successUrl, cancelUrl);
            String approvalUrl = payPalService.getApprovalUrl(paypalPayment);
            if (approvalUrl == null) {
                throw new PayPalApiException("PayPal did not return an approval URL");
            }
            return new PaymentInitiationResult(
                    approvalUrl, paypalPayment.getId(), objectMapper.writeValueAsString(paypalPayment));
        } catch (PayPalRESTException e) {
            throw new PayPalApiException("PayPal create-payment failed: " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw new PayPalApiException("Failed to serialize PayPal response", e);
        }
    }

    @Override
    public PaymentRefundResult refund(Payment payment, BigDecimal amount) {
        try {
            com.paypal.api.payments.DetailedRefund refund =
                    payPalService.refundPayment(payment.getTransactionId(), amount);
            return new PaymentRefundResult(refund.getId(), objectMapper.writeValueAsString(refund));
        } catch (PayPalRESTException e) {
            throw new PayPalApiException("PayPal refund failed: " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw new PayPalApiException("Failed to serialize PayPal refund response", e);
        }
    }
}
