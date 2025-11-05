package com.ut.edu.backend.service.inter;

import com.paypal.api.payments.DetailedRefund;
import com.paypal.api.payments.Payment;
import com.paypal.base.rest.PayPalRESTException;
import com.ut.edu.backend.model.Order;

import java.math.BigDecimal;

/**
 * PayPal Service Interface
 * Handles PayPal payment operations
 */
public interface IPayPalService {

    /**
     * Create PayPal payment
     */
    Payment createPayment(Order order, String successUrl, String cancelUrl) throws PayPalRESTException;

    /**
     * Execute/capture payment
     */
    Payment executePayment(String paymentId, String payerId) throws PayPalRESTException;

    /**
     * Get payment details
     */
    Payment getPaymentDetails(String paymentId) throws PayPalRESTException;

    /**
     * Refund payment
     */
    DetailedRefund refundPayment(String saleId, BigDecimal amount) throws PayPalRESTException;

    /**
     * Get approval URL from payment
     */
    String getApprovalUrl(Payment payment);

    /**
     * Verify webhook signature
     */
    boolean verifyWebhookSignature(String payload, String signature);

    /**
     * Calculate PayPal fee
     */
    BigDecimal calculatePayPalFee(BigDecimal amount);

    /**
     * Get sale ID from executed payment
     */
    String getSaleId(Payment payment);
}
