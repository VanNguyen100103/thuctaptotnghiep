package com.ut.edu.backend.order;

import com.ut.edu.backend.payment.Payment;
import com.ut.edu.backend.sale.Customer;
import com.ut.edu.backend.sale.Sale;
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
 *
 * An order reaches this screen from either of two directions - a customer
 * checking out on the storefront, or a "Bán giao hàng" sale at the register -
 * so every customer-facing field here reads the account when there is one and
 * falls back to the walk-in Customer card when there is not.
 */
public record StoreOrderResponse(
        Long id,
        /** "Mã đặt hàng" - Order.orderNumber. */
        String code,
        String status,
        LocalDateTime createdAt,
        /** "Mã KH" - the walk-in's own code, or one rendered from the buyer's user id the way KiotViet renders its own. */
        String customerCode,
        String customerName,
        String customerPhone,
        String customerEmail,
        /** "Người nhận" - falls back to the buyer, who is the recipient unless the order says otherwise. */
        String recipientName,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        /** "Thu khác" - the register's catch-all surcharge; zero on a storefront order. */
        BigDecimal otherCollectionAmount,
        BigDecimal shippingCost,
        BigDecimal taxAmount,
        /** "Khách cần trả". */
        BigDecimal total,
        /** "Khách đã trả" - what was actually collected, net of refunds and of change given back. */
        BigDecimal amountPaid,
        String paymentMethod,
        String paymentStatus,
        String shippingAddress,
        /** Tỉnh/TP, on its own so the "Khu vực giao hàng" filter has something to match. */
        String shippingProvince,
        /** Quận/Huyện. */
        String shippingDistrict,
        String shippingWard,
        String trackingNumber,
        String shippingCarrier,
        String notes,
        /** "Kênh bán". */
        String salesChannel,
        /** The star column. */
        boolean starred,
        /** "Người tạo" - the staff member who rang it up; null when the customer placed it themselves. */
        String createdBy,
        /** "Thời gian giao hàng" - when the shop promised it. */
        LocalDateTime expectedDeliveryAt,
        /** The invoice a register order was rung up as, so the row can link across to Hóa đơn. */
        String saleCode,
        Long saleId,
        /** Set on the sources of a "Gộp đơn" - what they were folded into. */
        String mergedIntoCode,
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
        Customer customer = order.getCustomer();
        Payment payment = order.getPayment();
        Sale sale = order.getSale();
        Order mergedInto = order.getMergedInto();
        String buyerName = customer != null ? customer.getName() : customerName(user);
        return new StoreOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getCreatedAt(),
                customerCode(user, customer),
                buyerName,
                customer != null ? customer.getPhone()
                        : user != null ? user.getPhoneNumber() : order.getShippingPhoneNumber(),
                customer != null ? customer.getEmail()
                        : user != null ? user.getEmail() : order.getShippingEmail(),
                order.getRecipientName() != null ? order.getRecipientName() : buyerName,
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getOtherCollectionAmount(),
                order.getShippingCost(),
                order.getTaxAmount(),
                order.getTotal(),
                amountPaid(order, payment, sale),
                paymentMethod(payment, sale),
                payment != null && payment.getStatus() != null ? payment.getStatus().name() : null,
                shippingAddress(order),
                order.getShippingCity(),
                order.getShippingStateProvince(),
                order.getShippingWard(),
                order.getTrackingNumber(),
                order.getShippingCarrier(),
                order.getNotes(),
                order.getSalesChannel() != null ? order.getSalesChannel().name() : SalesChannel.STOREFRONT.name(),
                Boolean.TRUE.equals(order.getStarred()),
                order.getCreatedBy() != null ? order.getCreatedBy().getUsername() : null,
                order.getExpectedDeliveryAt(),
                sale != null ? sale.getCode() : null,
                sale != null ? sale.getId() : null,
                mergedInto != null ? mergedInto.getOrderNumber() : null,
                items);
    }

    /** A walk-in already carries the shop's own code; a storefront buyer's is rendered from their user id, the way KiotViet renders its own. */
    private static String customerCode(User user, Customer customer) {
        if (customer != null) {
            return customer.getCode();
        }
        return user != null ? String.format("KH%06d", user.getId()) : null;
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
     *
     * A register order has no Payment at all: its money was taken at the till
     * and recorded on the Sale - except when the courier is the one collecting
     * it (PENDING_COD), where nothing has been collected yet either.
     */
    private static BigDecimal amountPaid(Order order, Payment payment, Sale sale) {
        if (payment == null || payment.getStatus() == null) {
            if (sale == null || order.getStatus() == OrderStatus.PENDING_COD) {
                return BigDecimal.ZERO;
            }
            BigDecimal received = sale.getAmountReceived() == null ? BigDecimal.ZERO : sale.getAmountReceived();
            // Change handed back is not money the shop kept.
            return order.getTotal() == null ? received : received.min(order.getTotal());
        }
        BigDecimal amount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
        BigDecimal refunded = payment.getRefundAmount() == null ? BigDecimal.ZERO : payment.getRefundAmount();
        return switch (payment.getStatus()) {
            case COMPLETED -> amount;
            case REFUNDED, PARTIALLY_REFUNDED -> amount.subtract(refunded).max(BigDecimal.ZERO);
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * A register order's tender lives on the Sale's payment lines, which can be
     * several at once; the first one names the row, the way the invoice list
     * already labels a split payment.
     */
    private static String paymentMethod(Payment payment, Sale sale) {
        if (payment != null && payment.getPaymentMethod() != null) {
            return payment.getPaymentMethod().name();
        }
        if (sale != null && sale.getPayments() != null && !sale.getPayments().isEmpty()) {
            return sale.getPayments().get(0).getMethod().name();
        }
        return null;
    }

    private static String shippingAddress(Order order) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, order.getShippingAddressLine1());
        addIfPresent(parts, order.getShippingAddressLine2());
        addIfPresent(parts, order.getShippingWard());
        addIfPresent(parts, order.getShippingStateProvince());
        addIfPresent(parts, order.getShippingCity());
        addIfPresent(parts, order.getShippingCountry());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }
}
