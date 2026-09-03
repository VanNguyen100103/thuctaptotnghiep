package com.ut.edu.backend.sale;

import java.math.BigDecimal;

public record SalePaymentResponse(Long id, SalePaymentMethod method, BigDecimal amount) {

    static SalePaymentResponse from(SalePayment payment) {
        return new SalePaymentResponse(payment.getId(), payment.getMethod(), payment.getAmount());
    }
}
