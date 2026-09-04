package com.ut.edu.backend.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Checkout body for "Bán hàng" - POST /store/sales always finalizes on
 * receipt (no draft step, unlike SavePurchaseOrderRequest). Must carry at
 * least one item and at least one payment line; SaleService validates the
 * payment lines sum to at least the invoice total.
 */
public record CreateSaleRequest(
        Long customerId,
        @DecimalMin(value = "0.0", message = "discountAmount cannot be negative") BigDecimal discountAmount,
        @DecimalMin(value = "0.0", message = "otherCollectionAmount cannot be negative") BigDecimal otherCollectionAmount,
        /** "Mã coupon" - validated and re-priced server-side in SaleService, never trusted from the client. */
        String couponCode,
        /** "Điểm" the cashier chose to redeem - must not exceed the selected customer's balance. */
        @Min(value = 0, message = "pointsToRedeem cannot be negative") Integer pointsToRedeem,
        String note,
        @NotEmpty(message = "Hóa đơn chưa có hàng hóa nào") @Valid List<SaleItemRequest> items,
        @NotEmpty(message = "Chưa chọn phương thức thanh toán") @Valid List<SalePaymentRequest> payments) {
}
