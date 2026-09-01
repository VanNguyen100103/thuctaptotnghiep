package com.ut.edu.backend.payment;

/** Result of {@link PaymentProvider#refund}. */
public record PaymentRefundResult(String refundReference, String rawResponseJson) {
}
