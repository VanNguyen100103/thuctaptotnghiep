package com.ut.edu.backend.payment;

/**
 * Lifecycle of a POS "Chuyển khoản" QR. Deliberately not
 * {@link PaymentStatus}: that enum's states (PROCESSING, CANCELLED,
 * PARTIALLY_REFUNDED) belong to a gateway that this flow has none of -
 * SePay only ever tells us "money arrived", so there are exactly two
 * states a counter QR can be in.
 */
public enum PosPaymentSessionStatus {
    /** QR shown, nothing received yet. Stale sessions stay here forever - harmless, and cheaper than a sweeper job. */
    PENDING,
    /** A transfer covering the amount landed on the account. */
    PAID
}
