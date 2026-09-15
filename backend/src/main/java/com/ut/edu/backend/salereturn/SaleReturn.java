package com.ut.edu.backend.salereturn;

import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.sale.Customer;
import com.ut.edu.backend.sale.Sale;
import com.ut.edu.backend.sale.SalePaymentMethod;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;
import com.ut.edu.backend.user.User;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * "Trả hàng" - goods a customer brings back, against the invoice they bought
 * them on. The counter-side mirror of PurchaseReturn, which sends goods the
 * other way, back to the supplier.
 *
 * Modelled on {@link Sale}, not on PurchaseReturn, despite sharing the word:
 * a return at the register is atomic the way checkout is. The customer is
 * standing at the counter and the money changes hands there and then, so
 * there is no "Phiếu tạm" to save and come back to and therefore no status at
 * all - SaleReturnService#create both writes the document and applies its
 * stock and loyalty effects, exactly as SaleService#checkout does.
 *
 * Always tied to a {@link #sale}. A refund with no invoice behind it is money
 * leaving the till that nothing accounts for, and the link is also what makes
 * "how much of this line is still returnable" answerable - see
 * SaleReturnRepository#sumReturnedQuantitiesBySale.
 */
@Entity
@Table(name = "sale_returns", indexes = {
    @Index(name = "idx_sale_returns_store", columnList = "store_id"),
    @Index(name = "idx_sale_returns_sale", columnList = "sale_id"),
    @Index(name = "idx_sale_returns_customer", columnList = "customer_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_sale_returns_store_code", columnNames = {"store_id", "code"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "sale", "customer", "createdBy", "items"})
public class SaleReturn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    /** Auto-generated "Mã trả hàng" - TH000001, TH000002, ... per store. */
    @Column(nullable = false, length = 30)
    private String code;

    /** "Hóa đơn" the goods came off. Never null - see the class note. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonIgnore
    private Sale sale;

    /** Snapshotted off the invoice; null when it was a walk-in sale ("khách lẻ"). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @JsonIgnore
    private Customer customer;

    /** "Tổng tiền hàng trả lại" - the returned lines at the prices the invoice charged, not at today's catalog price. */
    @Column(name = "total_goods_value", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalGoodsValue = BigDecimal.ZERO;

    /**
     * "Giảm giá phân bổ" - the returned goods' share of the discounts the
     * whole invoice received (giảm giá hóa đơn + coupon + điểm đã dùng).
     * Refunding without it would hand back money the customer never paid.
     */
    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** "Phí trả hàng" - what the shop keeps for taking the goods back; the only figure on this document the user types. */
    @Column(name = "return_fee", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal returnFee = BigDecimal.ZERO;

    /** "Cần trả khách" = totalGoodsValue - discountAmount - returnFee, never below zero. */
    @Column(name = "refund_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;

    /**
     * Which till the refund leaves by - the same four tenders a sale is paid
     * with. One method rather than a list of them: splitting a payment across
     * tenders is something a counter really does, splitting a refund is not.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", nullable = false, length = 20)
    @Builder.Default
    private SalePaymentMethod refundMethod = SalePaymentMethod.CASH;

    /**
     * Whether the refund has actually reached the customer. Separate from the
     * receipt itself, which is final on creation - see
     * {@link SaleReturnRefundStatus}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 20)
    @Builder.Default
    private SaleReturnRefundStatus refundStatus = SaleReturnRefundStatus.REFUNDED;

    /** When the money reached the customer - distinct from createdAt, which is when the goods came back. */
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    /** SePay's reference for the outgoing transfer, when the webhook settled this; null when a person ticked it off. */
    @Column(name = "refund_reference", length = 200)
    private String refundReference;

    /** "Điểm đã dùng" given back - the customer spent them on goods they no longer have. */
    @Column(name = "points_restored", nullable = false)
    @Builder.Default
    private Integer pointsRestored = 0;

    /** "Điểm tích lũy" clawed back - earned on a sale that has partly come undone. */
    @Column(name = "points_reverted", nullable = false)
    @Builder.Default
    private Integer pointsReverted = 0;

    @Column(length = 1000)
    private String note;

    /** "Người trả hàng" - who ran the return at the register. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @JsonIgnore
    private User createdBy;

    @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleReturnItem> items = new ArrayList<>();

    public void addItem(SaleReturnItem item) {
        items.add(item);
        item.setSaleReturn(this);
    }

    /**
     * What the shop owner types into the transfer content when they send this
     * refund, and what the SePay webhook matches it back on - the same short,
     * digits-only shape orders use ("DH&lt;id&gt;"), for the same reason: a
     * banking app will carry "TH12" through a real transfer intact where it
     * would mangle anything longer. Derived, never stored - the id IS the
     * reference.
     */
    public String transferContent() {
        return id == null ? null : "TH" + id;
    }

    /** "Đã hoàn tiền" - the money reached the customer. {@code reference} is SePay's, or null when marked by hand. */
    public void markRefunded(String reference) {
        this.refundStatus = SaleReturnRefundStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
        this.refundReference = reference;
    }

    public boolean isAwaitingTransfer() {
        return refundStatus == SaleReturnRefundStatus.PENDING;
    }
}
