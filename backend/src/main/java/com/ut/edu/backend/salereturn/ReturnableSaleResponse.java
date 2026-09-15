package com.ut.edu.backend.salereturn;

import com.ut.edu.backend.sale.Sale;
import com.ut.edu.backend.sale.SaleItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What the "Trả hàng" form loads when it opens on an invoice: the invoice
 * header, its tenders, and every line with how many units are still
 * returnable after whatever earlier returns already took.
 *
 * A shape of its own rather than SaleResponse plus a map on the side. The
 * form asks one question SaleResponse cannot answer - "how much of this line
 * may I still hand back" - and answering it in the same payload keeps the
 * screen from being able to show a stale cap.
 */
public record ReturnableSaleResponse(
        Long saleId,
        String saleCode,
        LocalDateTime saleCreatedAt,
        Long customerId,
        String customerCode,
        String customerName,
        String customerPhone,
        Integer customerLoyaltyPoints,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        String couponCode,
        BigDecimal couponDiscountAmount,
        Integer pointsRedeemed,
        BigDecimal pointsRedeemedAmount,
        Integer pointsEarned,
        /**
         * What earlier returns of this invoice already put back and took back.
         * The form needs both to show the same point figures the next return
         * is actually going to move - they are what caps them.
         */
        Integer pointsAlreadyRestored,
        Integer pointsAlreadyReverted,
        BigDecimal shippingFee,
        BigDecimal otherCollectionAmount,
        BigDecimal totalAmount,
        /** How the invoice was settled - the form defaults the refund to the tender that brought in the most. */
        List<Tender> payments,
        List<ReturnableLine> lines) {

    public record Tender(String method, BigDecimal amount) {
    }

    /**
     * @param soldQuantity      what the invoice line sold
     * @param returnedQuantity  what earlier returns already took back
     * @param returnableQuantity what is left - the cap on this form's input
     */
    public record ReturnableLine(
            Long saleItemId,
            Long productId,
            String productName,
            String productSku,
            Integer soldQuantity,
            Integer returnedQuantity,
            Integer returnableQuantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal lineTotal) {
    }

    static ReturnableSaleResponse from(Sale sale, Map<Long, Integer> returnedBySaleItemId,
                                       int pointsAlreadyRestored, int pointsAlreadyReverted) {
        List<ReturnableLine> lines = sale.getItems().stream()
                .map(item -> toLine(item, returnedBySaleItemId.getOrDefault(item.getId(), 0)))
                .collect(Collectors.toList());

        return new ReturnableSaleResponse(
                sale.getId(),
                sale.getCode(),
                sale.getCreatedAt(),
                sale.getCustomer() != null ? sale.getCustomer().getId() : null,
                sale.getCustomer() != null ? sale.getCustomer().getCode() : null,
                sale.getCustomer() != null ? sale.getCustomer().getName() : null,
                sale.getCustomer() != null ? sale.getCustomer().getPhone() : null,
                sale.getCustomer() != null ? sale.getCustomer().getLoyaltyPoints() : null,
                sale.getSubtotal(),
                sale.getDiscountAmount(),
                sale.getCouponCode(),
                sale.getCouponDiscountAmount(),
                sale.getPointsRedeemed(),
                sale.getPointsRedeemedAmount(),
                sale.getPointsEarned(),
                pointsAlreadyRestored,
                pointsAlreadyReverted,
                sale.getShippingFee(),
                sale.getOtherCollectionAmount(),
                sale.getTotalAmount(),
                sale.getPayments().stream()
                        .map(p -> new Tender(p.getMethod().name(), p.getAmount()))
                        .collect(Collectors.toList()),
                lines);
    }

    private static ReturnableLine toLine(SaleItem item, int alreadyReturned) {
        return new ReturnableLine(
                item.getId(),
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductName(),
                item.getProductSku(),
                item.getQuantity(),
                alreadyReturned,
                Math.max(0, item.getQuantity() - alreadyReturned),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getLineTotal());
    }
}
