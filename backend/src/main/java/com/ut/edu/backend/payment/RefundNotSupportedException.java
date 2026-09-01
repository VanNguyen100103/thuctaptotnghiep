package com.ut.edu.backend.payment;

/** Thrown by a PaymentProvider whose refund API isn't integrated yet. */
public class RefundNotSupportedException extends RuntimeException {

    public RefundNotSupportedException(String message) {
        super(message);
    }
}
