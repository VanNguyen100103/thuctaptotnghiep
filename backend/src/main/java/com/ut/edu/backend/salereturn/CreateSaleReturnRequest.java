package com.ut.edu.backend.salereturn;

import com.ut.edu.backend.sale.SalePaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * "Trả hàng" in one request. There is no draft step (see SaleReturn), so
 * unlike SavePurchaseReturnRequest this body has to be complete: an empty
 * line list is not a document the counter can save for later, it is a refund
 * of nothing.
 */
public record CreateSaleReturnRequest(
        @NotNull(message = "saleId is required") Long saleId,
        /** "Phí trả hàng" - withheld from the refund; the only money figure the client sends. */
        @DecimalMin(value = "0.0", message = "returnFee cannot be negative") BigDecimal returnFee,
        @NotNull(message = "refundMethod is required") SalePaymentMethod refundMethod,
        String note,
        @NotEmpty(message = "Phiếu trả hàng chưa có hàng hóa nào") @Valid List<SaleReturnItemRequest> items) {
}
