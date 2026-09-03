package com.ut.edu.backend.sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record SaleResponse(
        Long id,
        String code,
        Long customerId,
        String customerCode,
        String customerName,
        String customerPhone,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal otherCollectionAmount,
        BigDecimal totalAmount,
        BigDecimal amountReceived,
        /** "Tiền thừa trả khách" = amountReceived - totalAmount when paid in cash-like tenders over the total. Purely derived, not persisted. */
        BigDecimal changeAmount,
        String note,
        String createdByUsername,
        LocalDateTime createdAt,
        List<SaleItemResponse> items,
        List<SalePaymentResponse> payments) {

    static SaleResponse from(Sale sale) {
        return new SaleResponse(
                sale.getId(),
                sale.getCode(),
                sale.getCustomer() != null ? sale.getCustomer().getId() : null,
                sale.getCustomer() != null ? sale.getCustomer().getCode() : null,
                sale.getCustomer() != null ? sale.getCustomer().getName() : null,
                sale.getCustomer() != null ? sale.getCustomer().getPhone() : null,
                sale.getSubtotal(),
                sale.getDiscountAmount(),
                sale.getOtherCollectionAmount(),
                sale.getTotalAmount(),
                sale.getAmountReceived(),
                sale.getAmountReceived().subtract(sale.getTotalAmount()).max(BigDecimal.ZERO),
                sale.getNote(),
                sale.getCreatedBy() != null ? sale.getCreatedBy().getUsername() : null,
                sale.getCreatedAt(),
                sale.getItems().stream().map(SaleItemResponse::from).collect(Collectors.toList()),
                sale.getPayments().stream().map(SalePaymentResponse::from).collect(Collectors.toList()));
    }
}
