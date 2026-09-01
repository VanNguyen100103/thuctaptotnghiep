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
}
