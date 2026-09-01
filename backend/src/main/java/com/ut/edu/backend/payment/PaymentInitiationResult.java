package com.ut.edu.backend.payment;

/**
 * Result of {@link PaymentProvider#createPayment}.
 *
 * @param redirectUrl        where the frontend sends the browser to complete payment
 * @param gatewayReferenceId provider's own id for this attempt (PayPal payment id);
 *                           null for providers that don't need one persisted
 *                           (MoMo's IPN carries everything needed to look the row back up)
 * @param rawResponseJson    raw gateway response, stored in Payment.paymentDetails
 */
public record PaymentInitiationResult(String redirectUrl, String gatewayReferenceId, String rawResponseJson) {
}
