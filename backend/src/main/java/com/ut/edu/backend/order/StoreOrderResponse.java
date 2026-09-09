package com.ut.edu.backend.order;

import com.ut.edu.backend.payment.Payment;
import com.ut.edu.backend.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The dashboard's view of a customer order - KiotViet's "Đặt hàng" screen.
 *
 * The Order entity itself cannot be serialized to this screen: its user,
 * store and coupon are @JsonIgnore'd (they carry the buyer's credentials and
 * the whole tenant behind them), so the customer columns the list needs would
 * come back empty. Same "one DTO, items absent on list rows" shape as
 * PurchaseOrderResponse.
 */
public record StoreOrderResponse(
        Long id,
        /** "Mã đặt hàng" - Order.orderNumber. */
        String code,
        String status,
        LocalDateTime createdAt,
        /** "Mã KH" - rendered from the buyer's user id, the way KiotViet renders its own customer codes. Not a stored column. */
        String customerCode,
        String customerName,
        String customerPhone,
        String customerEmail,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingCost,
        BigDecimal taxAmount,
        /** "Khách cần trả". */
        BigDecimal total,
        /** "Khách đã trả" - what the payment record actually settled, net of refunds. */
        BigDecimal amountPaid,
        String paymentMethod,
        String paymentStatus,
        String shippingAddress,
        String trackingNumber,
        String shippingCarrier,
        String notes,
        /** Populated on the detail endpoint; null on list rows. */
        List<StoreOrderItemResponse> items) {

    static StoreOrderResponse summary(Order order) {
        return build(order, null);
    }

    static StoreOrderResponse detail(Order order) {
        return build(order, order.getItems().stream()
                .map(StoreOrderItemResponse::from)
                .collect(Collectors.toList()));
    }

    private static StoreOrderResponse build(Order order, List<StoreOrderItemResponse> items) {
        User user = order.getUser();
        Payment payment = order.getPayment();
        return new StoreOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getCreatedAt(),
                user != null ? String.format("KH%06d", user.getId()) : null,
                customerName(user),
                user != null ? user.getPhoneNumber() : order.getShippingPhoneNumber(),
                user != null ? user.getEmail() : order.getShippingEmail(),
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getShippingCost(),
                order.getTaxAmount(),
                order.getTotal(),
                amountPaid(payment),
                payment != null && payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null,
                payment != null && payment.getStatus() != null ? payment.getStatus().name() : null,
                shippingAddress(order),
                order.getTrackingNumber(),
                order.getShippingCarrier(),
                order.getNotes(),
                items);
    }

    /** Falls back to the username: a storefront account only fills in a real name at checkout. */
    private static String customerName(User user) {
        if (user == null) {
            return null;
        }
        String full = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return full.isEmpty() ? user.getUsername() : full;
    }

    /**
     * Only a settled payment counts as collected, and a refund gives the money
     * back - so a refunded order reads as unpaid rather than paid-then-owed.
     * COD orders show 0 until the delivery marks them paid, which is what the
     * shop is actually chasing.
     */
    private static BigDecimal amountPaid(Payment payment) {
        if (payment == null || payment.getStatus() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
        BigDecimal refunded = payment.getRefundAmount() == null ? BigDecimal.ZERO : payment.getRefundAmount();
        return switch (payment.getStatus()) {
            case COMPLETED -> amount;
            case REFUNDED, PARTIALLY_REFUNDED -> amount.subtract(refunded).max(BigDecimal.ZERO);
            default -> BigDecimal.ZERO;
        };
    }

    private static String shippingAddress(Order order) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, order.getShippingAddressLine1());
        addIfPresent(parts, order.getShippingAddressLine2());
        addIfPresent(parts, order.getShippingCity());
        addIfPresent(parts, order.getShippingStateProvince());
        addIfPresent(parts, order.getShippingCountry());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }
}
