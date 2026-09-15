package com.ut.edu.backend.purchasereturn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Single response shape for both the list ("items" left null) and
 * detail/create/update endpoints, the same way PurchaseOrderResponse works.
 */
public record PurchaseReturnResponse(
        Long id,
        String code,
        Long supplierId,
        String supplierCode,
        String supplierName,
        String status,
        BigDecimal totalGoodsValue,
        BigDecimal discountAmount,
        /** "Nhà cung cấp cần trả" - the gross refund owed back, before anything already handed over. */
        BigDecimal refundAmount,
        /** "Nhà cung cấp đã trả" - refunded in cash at return time. */
        BigDecimal amountReceived,
        /**
         * "Tính vào công nợ" = refundAmount - amountReceived: what this
         * return takes OFF the debt owed to the supplier, so it reads
         * positive where a receipt's own debtAmount reads negative. Not
         * persisted - purely derived.
         */
        BigDecimal debtAmount,
        String note,
        String createdByUsername,
        /** "Người trả" - who clicked "Hoàn thành"; null while still DRAFT. */
        String completedByUsername,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        boolean starred,
        List<PurchaseReturnItemResponse> items) {

    static PurchaseReturnResponse summary(PurchaseReturn pr) {
        return build(pr, null);
    }

    static PurchaseReturnResponse detail(PurchaseReturn pr) {
        return build(pr, pr.getItems().stream().map(PurchaseReturnItemResponse::from).collect(Collectors.toList()));
    }

    private static PurchaseReturnResponse build(PurchaseReturn pr, List<PurchaseReturnItemResponse> items) {
        return new PurchaseReturnResponse(
                pr.getId(),
                pr.getCode(),
                pr.getSupplier() != null ? pr.getSupplier().getId() : null,
                pr.getSupplier() != null ? pr.getSupplier().getCode() : null,
                pr.getSupplier() != null ? pr.getSupplier().getName() : null,
                pr.getStatus().name(),
                pr.getTotalGoodsValue(),
                pr.getDiscountAmount(),
                pr.getRefundAmount(),
                pr.getAmountReceived(),
                pr.getRefundAmount().subtract(pr.getAmountReceived()),
                pr.getNote(),
                pr.getCreatedBy() != null ? pr.getCreatedBy().getUsername() : null,
                pr.getCompletedBy() != null ? pr.getCompletedBy().getUsername() : null,
                pr.getCreatedAt(),
                pr.getCompletedAt(),
                Boolean.TRUE.equals(pr.getStarred()),
                items);
    }
}
