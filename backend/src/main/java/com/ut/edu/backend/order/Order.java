package com.ut.edu.backend.order;

import com.ut.edu.backend.sale.Customer;
import com.ut.edu.backend.shipping.goship.Shipment;
import com.ut.edu.backend.sale.Sale;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.payment.Payment;
import com.ut.edu.backend.coupon.Coupon;
import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Order entity for customer orders
 * Publishes events to Kafka for async processing
 */
@Entity
@Table(name = "orders", uniqueConstraints = {
    // Per shop, not global: "Mã đặt hàng" restarts at DH000001 in every store.
    @UniqueConstraint(name = "uk_orders_store_order_number", columnNames = {"store_id", "orderNumber"})
}, indexes = {
    @Index(name = "idx_order_user", columnList = "user_id"),
    @Index(name = "idx_order_number", columnList = "orderNumber"),
    @Index(name = "idx_order_status", columnList = "status"),
    @Index(name = "idx_order_created", columnList = "created_at"),
    @Index(name = "idx_orders_store", columnList = "store_id"),
    @Index(name = "idx_orders_customer", columnList = "customer_id"),
    @Index(name = "idx_orders_sale", columnList = "sale_id"),
    @Index(name = "idx_orders_created_by", columnList = "created_by_id")
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "user", "customer", "sale", "createdBy", "mergedInto", "items", "payment", "shipments"})
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant link: the store this order was placed in
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    @NotBlank(message = "Order number is required")
    @Column(nullable = false, length = 50)
    private String orderNumber;

    /**
     * The account that placed the order. Null for an order rung up at the
     * register: a walk-in has a {@link #customer} card, not a login.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    /** The walk-in buyer behind a "Bán giao hàng" order; null for a storefront one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @JsonIgnore
    private Customer customer;

    /**
     * The invoice a "Bán giao hàng" order was rung up as - the register takes
     * the money before the parcel leaves, so what was collected lives here
     * rather than on {@link #payment}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    @JsonIgnore
    private Sale sale;

    /** "Người tạo" - the staff member who rang it up; null when the customer placed it themselves. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @JsonIgnore
    private User createdBy;

    /** "Kênh bán". */
    @Enumerated(EnumType.STRING)
    @Column(name = "sales_channel", nullable = false, length = 30)
    @Builder.Default
    private SalesChannel salesChannel = SalesChannel.STOREFRONT;

    /** The list's ★ column - the shop's own flag, nothing in the lifecycle reads it. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean starred = false;

    /** "Thời gian giao hàng" - when the shop promised it, filtered separately from when it was placed. */
    @Column(name = "expected_delivery_at")
    private LocalDateTime expectedDeliveryAt;

    /**
     * Parcels booked for this order. A list rather than one, because a refused
     * booking gets re-booked and the first attempt is still part of the record;
     * the screens read the newest (see latestShipment()).
     */
    @OneToMany(mappedBy = "order")
    @JsonIgnore
    @Builder.Default
    private List<Shipment> shipments = new ArrayList<>();

    /** Set on the sources of a "Gộp đơn" - they are cancelled, and this says what they became. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_order_id")
    @JsonIgnore
    private Order mergedInto;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<OrderItem> items = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @NotNull(message = "Subtotal is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @NotNull(message = "Shipping cost is required")
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** "Thu khác" - the register's catch-all surcharge; zero on a storefront order, which has no field for one. */
    @Column(name = "other_collection_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal otherCollectionAmount = BigDecimal.ZERO;

    @NotNull(message = "Total is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    // Shipping address
    /** "Người nhận" - who the parcel is addressed to, when that is not the buyer. */
    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    @NotBlank(message = "Shipping address is required")
    @Column(nullable = false)
    private String shippingAddressLine1;

    private String shippingAddressLine2;

    @NotBlank(message = "Shipping city is required")
    @Column(nullable = false, length = 100)
    private String shippingCity;

    /** Quận/Huyện - see the storefront checkout form's own labels; {@link #shippingCity} is Tỉnh/TP. */
    @NotBlank(message = "Shipping state/province is required")
    @Column(nullable = false, length = 100)
    private String shippingStateProvince;

    /** Phường/Xã. */
    @Column(name = "shipping_ward", length = 100)
    private String shippingWard;

    /** Optional: a Vietnamese address is Tỉnh/Quận/Phường, and no screen here collects a postal code. */
    @Column(length = 20)
    private String shippingPostalCode;

    @Column(length = 100)
    private String shippingCountry;

    @Column(length = 20)
    private String shippingPhoneNumber;

    @Column(length = 100)
    private String shippingEmail;

    // Billing address (optional, can be same as shipping)
    private String billingAddressLine1;
    private String billingAddressLine2;

    @Column(length = 100)
    private String billingCity;

    @Column(length = 100)
    private String billingStateProvince;

    @Column(length = 20)
    private String billingPostalCode;

    @Column(length = 100)
    private String billingCountry;

    // Tracking information
    @Column(length = 100)
    private String trackingNumber;

    @Column(length = 100)
    private String shippingCarrier;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String adminNotes;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private Payment payment;

    // Coupon/discount
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    @JsonIgnore
    private Coupon coupon;

    @Column(length = 50)
    private String couponCode;  // Store code for reference even if coupon is deleted

    /** The booking that speaks for this order now - the most recent one, or none. */
    public Shipment latestShipment() {
        return shipments == null ? null
                : shipments.stream()
                        .max(Comparator.comparing(Shipment::getCreatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .orElse(null);
    }

    // Helper methods
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    public void calculateTotal() {
        this.total = subtotal
                    .add(shippingCost)
                    .add(taxAmount)
                    .add(otherCollectionAmount == null ? BigDecimal.ZERO : otherCollectionAmount)
                    .subtract(discountAmount);
    }

    /** Kept in step with OrderStatusValidator's own PROCESSING -> CANCELLED edge: an order the shop is still packing can still be called off. */
    public boolean canBeCancelled() {
        return status == OrderStatus.PENDING ||
               status == OrderStatus.PAYMENT_PENDING ||
               status == OrderStatus.PENDING_COD ||
               status == OrderStatus.PAID ||
               status == OrderStatus.PROCESSING;
    }

    public boolean canBeRefunded() {
        return status == OrderStatus.PAID ||
               status == OrderStatus.PROCESSING ||
               status == OrderStatus.SHIPPED;
    }
}
