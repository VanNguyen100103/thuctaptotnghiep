package com.ut.edu.backend.sale;

import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;
import com.ut.edu.backend.user.User;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A completed "Bán hàng" (POS) sale - the in-store checkout screen, reached
 * via the "Bán hàng" button rather than a dashboard tab, matching KiotViet's
 * own layout. Unlike Nhập hàng (PurchaseOrder), there is no draft/"Phiếu
 * tạm" state here: a Sale is only ever created once payment is confirmed
 * ("Thanh toán" completes it on the spot), so SaleService#checkout both
 * creates the row and applies its stock decrement in one step. See the V19
 * migration for why this is a separate module from Order/Payment rather
 * than a reuse of them.
 */
@Entity
@Table(name = "sales", indexes = {
    @Index(name = "idx_sales_store", columnList = "store_id"),
    @Index(name = "idx_sales_customer", columnList = "customer_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_sales_store_code", columnNames = {"store_id", "code"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "customer", "createdBy", "items", "payments"})
public class Sale extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    /** Auto-generated "Số hóa đơn" - HD000001, HD000002, ... per store. */
    @Column(nullable = false, length = 30)
    private String code;

    /** "Khách hàng" - optional; a walk-in sale with no customer attached is valid ("khách lẻ"). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @JsonIgnore
    private Customer customer;

    /** "Tổng tiền hàng" - sum of every line's lineTotal. Recomputed server-side, never trusted from the client. */
    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    /** "Giảm giá" - invoice-level discount. */
    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** "Thu khác" - an extra charge added on top (e.g. a service fee), the mirror of Nhập hàng's otherCosts but on the sale side. */
    @Column(name = "other_collection_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal otherCollectionAmount = BigDecimal.ZERO;

    /** "Khách cần trả" = subtotal - discountAmount + otherCollectionAmount. */
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** "Khách thanh toán" - sum of every SalePayment line; may exceed totalAmount (cash change), never less (enforced at checkout). */
    @Column(name = "amount_received", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal amountReceived = BigDecimal.ZERO;

    /** "Mã coupon" applied at checkout - snapshotted (not a FK) so the invoice stays accurate if the coupon is later edited/deactivated. */
    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    /** Discount contributed by {@link #couponCode}, computed server-side from the coupon's own rules - see SaleService. */
    @Column(name = "coupon_discount_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal couponDiscountAmount = BigDecimal.ZERO;

    /** "Điểm" redeemed against this sale - deducted from the customer's balance at checkout. */
    @Column(name = "points_redeemed", nullable = false)
    @Builder.Default
    private Integer pointsRedeemed = 0;

    /** Discount contributed by {@link #pointsRedeemed} (1 point = 1,000 VND). */
    @Column(name = "points_redeemed_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal pointsRedeemedAmount = BigDecimal.ZERO;

    /** "Tích điểm" earned from this sale's loyalty-eligible lines - credited to the customer's balance at checkout. */
    @Column(name = "points_earned", nullable = false)
    @Builder.Default
    private Integer pointsEarned = 0;

    @Column(length = 1000)
    private String note;

    /** "Nhân viên bán hàng" - who ran the register for this sale. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @JsonIgnore
    private User createdBy;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalePayment> payments = new ArrayList<>();

    public void addItem(SaleItem item) {
        items.add(item);
        item.setSale(this);
    }

    public void addPayment(SalePayment payment) {
        payments.add(payment);
        payment.setSale(this);
    }
}
