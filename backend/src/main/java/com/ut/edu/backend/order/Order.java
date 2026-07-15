package com.ut.edu.backend.order;

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
import java.util.HashSet;
import java.util.Set;

/**
 * Order entity for customer orders
 * Publishes events to Kafka for async processing
 */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_user", columnList = "user_id"),
    @Index(name = "idx_order_number", columnList = "orderNumber"),
    @Index(name = "idx_order_status", columnList = "status"),
    @Index(name = "idx_order_created", columnList = "created_at"),
    @Index(name = "idx_orders_store", columnList = "store_id")
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "user", "items", "payment"})
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
    @Column(unique = true, nullable = false, length = 50)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

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

    @NotNull(message = "Total is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    // Shipping address
    @NotBlank(message = "Shipping address is required")
    @Column(nullable = false)
    private String shippingAddressLine1;

    private String shippingAddressLine2;

    @NotBlank(message = "Shipping city is required")
    @Column(nullable = false, length = 100)
    private String shippingCity;

    @NotBlank(message = "Shipping state/province is required")
    @Column(nullable = false, length = 100)
    private String shippingStateProvince;

    @NotBlank(message = "Shipping postal code is required")
    @Column(nullable = false, length = 20)
    private String shippingPostalCode;

    @NotBlank(message = "Shipping country is required")
    @Column(nullable = false, length = 100)
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
                    .subtract(discountAmount);
    }

    public boolean canBeCancelled() {
        return status == OrderStatus.PENDING ||
               status == OrderStatus.PAYMENT_PENDING ||
               status == OrderStatus.PAID;
    }

    public boolean canBeRefunded() {
        return status == OrderStatus.PAID ||
               status == OrderStatus.PROCESSING ||
               status == OrderStatus.SHIPPED;
    }
}
