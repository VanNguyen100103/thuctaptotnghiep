package com.ut.edu.backend.sale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** One tender line in the "Thanh toán nhiều phương thức" (split payment) dialog. */
public record SalePaymentRequest(
        @NotNull(message = "method is required") SalePaymentMethod method,
        @NotNull(message = "amount is required") @DecimalMin(value = "0.01", message = "amount must be greater than 0") BigDecimal amount) {
}
