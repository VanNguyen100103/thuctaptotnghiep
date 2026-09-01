package com.ut.edu.backend.payment;

import com.ut.edu.backend.order.Order;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Cash-on-Delivery: no gateway, no redirect, no future webhook/capture -
 * choosing COD at checkout IS the commitment. See
 * PaymentProvider#confirmsImmediately / PaymentController#createPayment for
 * how the controller reacts to that.
 */
@Component
public class CodPaymentProvider implements PaymentProvider {

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.CASH_ON_DELIVERY;
    }

    @Override
    public boolean confirmsImmediately() {
        return true;
    }

    @Override
    public PaymentInitiationResult createPayment(Order order, String successUrl, String cancelUrl) {
        // No gateway to redirect to, and nothing left to confirm - successUrl
        // (frontendUrl + "/payment/success") is the correct landing spot, not
        // a placeholder, since PaymentController has already confirmed the
        // order by the time this returns.
        return new PaymentInitiationResult(successUrl, null, null);
    }

    @Override
    public PaymentRefundResult refund(Payment payment, BigDecimal amount) {
        throw new RefundNotSupportedException(
                "Cash-on-Delivery has no gateway refund API - return the cash to the customer in person and record it manually");
    }
}
