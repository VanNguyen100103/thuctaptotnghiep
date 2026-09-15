package com.ut.edu.backend.salereturn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Single response shape for both the list ("items" left null) and the
 * detail/create endpoints, the same way SaleResponse and
 * PurchaseReturnResponse work.
 */
public record SaleReturnResponse(
        Long id,
        String code,
        Long saleId,
        /** "Mã hóa đơn" the goods came off - the list's link back to where this started. */
        String saleCode,
        Long customerId,
        String customerCode,
        String customerName,
        String customerPhone,
        BigDecimal totalGoodsValue,
        /** "Giảm giá phân bổ" - the returned goods' share of the invoice's own discounts. */
        BigDecimal discountAmount,
        BigDecimal returnFee,
        /** "Cần trả khách" - what the shop hands back. */
        BigDecimal refundAmount,
        String refundMethod,
        /** PENDING while the shop still owes this transfer, REFUNDED once the customer has the money. */
        String refundStatus,
        LocalDateTime refundedAt,
        /** SePay's reference for the outgoing transfer; null when a person ticked it off instead. */
        String refundReference,
        /**
         * What to type in the transfer content so the SePay webhook can settle
         * this receipt by itself. Only worth showing while it is still owed -
         * null otherwise, so no screen invites a second transfer.
         */
        String transferContent,
        Integer pointsRestored,
        Integer pointsReverted,
        /** Customer's loyalty balance after this return's restore/claw-back - null on a walk-in return. */
        Integer customerLoyaltyPoints,
        String note,
        String createdByUsername,
        LocalDateTime createdAt,
        List<SaleReturnItemResponse> items) {

    /** A row of the "Trả hàng" list: the header only, its lines left to the detail call. */
    static SaleReturnResponse summary(SaleReturn sr) {
        return build(sr, null);
    }

    static SaleReturnResponse detail(SaleReturn sr) {
        return build(sr, sr.getItems().stream().map(SaleReturnItemResponse::from).collect(Collectors.toList()));
    }

    private static SaleReturnResponse build(SaleReturn sr, List<SaleReturnItemResponse> items) {
        return new SaleReturnResponse(
                sr.getId(),
                sr.getCode(),
                sr.getSale() != null ? sr.getSale().getId() : null,
                sr.getSale() != null ? sr.getSale().getCode() : null,
                sr.getCustomer() != null ? sr.getCustomer().getId() : null,
                sr.getCustomer() != null ? sr.getCustomer().getCode() : null,
                sr.getCustomer() != null ? sr.getCustomer().getName() : null,
                sr.getCustomer() != null ? sr.getCustomer().getPhone() : null,
                sr.getTotalGoodsValue(),
                sr.getDiscountAmount(),
                sr.getReturnFee(),
                sr.getRefundAmount(),
                sr.getRefundMethod().name(),
                sr.getRefundStatus().name(),
                sr.getRefundedAt(),
                sr.getRefundReference(),
                sr.isAwaitingTransfer() ? sr.transferContent() : null,
                sr.getPointsRestored(),
                sr.getPointsReverted(),
                sr.getCustomer() != null ? sr.getCustomer().getLoyaltyPoints() : null,
                sr.getNote(),
                sr.getCreatedBy() != null ? sr.getCreatedBy().getUsername() : null,
                sr.getCreatedAt(),
                items);
    }
}
