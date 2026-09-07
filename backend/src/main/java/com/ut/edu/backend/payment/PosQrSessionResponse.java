package com.ut.edu.backend.payment;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * What the Bán hàng terminal needs to show a QR and then watch it: the image
 * to display, the reference printed under it (so a customer whose bank app
 * drops the pre-filled content can type it back in), and the settlement
 * state it polls for.
 */
public record PosQrSessionResponse(
        Long id,
        String reference,
        BigDecimal amount,
        String qrUrl,
        PosPaymentSessionStatus status,
        /** Null until something arrives; below `amount` means a short payment the cashier has to resolve. */
        BigDecimal transferredAmount,
        LocalDateTime paidAt,
        /**
         * How much longer to keep polling, relative rather than absolute on
         * purpose: LocalDateTime serializes without an offset, so a browser
         * in UTC+7 reading a server timestamp in UTC would place the deadline
         * seven hours in the past and stop watching immediately.
         */
        long expiresInSeconds) {

    public static PosQrSessionResponse from(PosPaymentSession session, String qrUrl) {
        long remaining = Duration.between(LocalDateTime.now(), session.getExpiresAt()).getSeconds();
        return new PosQrSessionResponse(
                session.getId(),
                session.getReference(),
                session.getAmount(),
                qrUrl,
                session.getStatus(),
                session.getTransferredAmount(),
                session.getPaidAt(),
                Math.max(0, remaining));
    }
}
