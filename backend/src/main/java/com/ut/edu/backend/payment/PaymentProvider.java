package com.ut.edu.backend.payment;

import com.ut.edu.backend.order.Order;

import java.math.BigDecimal;

/**
 * Strategy interface for a payment gateway's checkout-creation and refund
 * calls. Deliberately does NOT cover PayPal's explicit-capture step
 * (POST /payments/execute) - that's an artifact of PayPal's own API shape,
 * never dispatched polymorphically, and gateways like MoMo have no
 * equivalent (their confirmation is IPN-only). Webhook/IPN handling stays
 * per-provider too, since each has its own endpoint, payload shape and
 * verification scheme.
 */
public interface PaymentProvider {

    PaymentMethod getMethod();

    PaymentInitiationResult createPayment(Order order, String successUrl, String cancelUrl);

    PaymentRefundResult refund(Payment payment, BigDecimal amount);

    /**
     * True if createPayment() itself is the confirmation event - no external
     * gateway, no redirect-and-wait, no future webhook/capture call will ever
     * arrive (COD). PaymentController branches on this to decide whether to
     * park the order in PAYMENT_PENDING (default - PayPal/MoMo) or confirm it
     * inline, in the same request. Defaults to false so existing implementers
     * need zero changes.
     */
    default boolean confirmsImmediately() {
        return false;
    }
}
