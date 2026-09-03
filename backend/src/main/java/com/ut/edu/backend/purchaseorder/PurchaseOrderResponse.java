package com.ut.edu.backend.purchaseorder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Single response shape for both the list ("items" left null - the list
 * table never needs line items) and detail/create/update endpoints (where
 * "items" is populated) - same "one DTO, some fields absent depending on
 * context" approach the rest of this codebase uses for ProductDTO.
 */
public record PurchaseOrderResponse(
        Long id,
        String code,
        Long supplierId,
        String supplierCode,
        String supplierName,
        String status,
        BigDecimal totalGoodsValue,
        BigDecimal discountAmount,
        BigDecimal supplierChargeAmount,
        BigDecimal amountPaid,
        BigDecimal otherCosts,
        BigDecimal payableAmount,
        /** "Tính vào công nợ" = amountPaid - payableAmount, i.e. the negative of what's still unpaid - shown as a debit to the supplier's running debt, matching KiotViet's own negative-for-payable accounting convention. Not persisted - purely derived from payableAmount/amountPaid. */
        BigDecimal debtAmount,
        String note,
        String createdByUsername,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<PurchaseOrderItemResponse> items) {

    static PurchaseOrderResponse summary(PurchaseOrder po) {
        return build(po, null);
    }

    static PurchaseOrderResponse detail(PurchaseOrder po) {
        return build(po, po.getItems().stream().map(PurchaseOrderItemResponse::from).collect(Collectors.toList()));
    }

    private static PurchaseOrderResponse build(PurchaseOrder po, List<PurchaseOrderItemResponse> items) {
        return new PurchaseOrderResponse(
                po.getId(),
                po.getCode(),
                po.getSupplier() != null ? po.getSupplier().getId() : null,
                po.getSupplier() != null ? po.getSupplier().getCode() : null,
                po.getSupplier() != null ? po.getSupplier().getName() : null,
                po.getStatus().name(),
                po.getTotalGoodsValue(),
                po.getDiscountAmount(),
                po.getSupplierChargeAmount(),
                po.getAmountPaid(),
                po.getOtherCosts(),
                po.getPayableAmount(),
                po.getAmountPaid().subtract(po.getPayableAmount()),
                po.getNote(),
                po.getCreatedBy() != null ? po.getCreatedBy().getUsername() : null,
                po.getCreatedAt(),
                po.getCompletedAt(),
                items);
    }
}
