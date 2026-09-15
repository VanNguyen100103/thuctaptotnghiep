package com.ut.edu.backend.purchasereturn;

import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;
import com.ut.edu.backend.supplier.Supplier;
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
 * "Trả hàng nhập" - goods going back to the supplier they came from. The
 * mirror image of {@link com.ut.edu.backend.purchaseorder.PurchaseOrder}:
 * same document shape and the same three states, but completing one takes
 * stock out of the shop rather than putting it in, and the money is what the
 * supplier owes back rather than what is owed to them.
 *
 * Deliberately NOT modelled as a negative PurchaseOrder, even though that
 * would have saved two tables: every screen that reads receipts (the Nhập
 * hàng list, its totals row, "Tổng mua") would then have to remember to
 * exclude negative rows, and one place forgetting is a wrong number a shop
 * cannot explain. A separate document is also what KiotViet shows - THN
 * codes sit beside PN codes in the supplier's history.
 */
@Entity
@Table(name = "purchase_returns", indexes = {
    @Index(name = "idx_purchase_returns_store", columnList = "store_id"),
    @Index(name = "idx_purchase_returns_status", columnList = "status"),
    @Index(name = "idx_purchase_returns_supplier", columnList = "supplier_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_purchase_returns_store_code", columnNames = {"store_id", "code"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "supplier", "createdBy", "completedBy", "items"})
public class PurchaseReturn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    /** Auto-generated "Mã trả hàng" - THN000001, THN000002, ... per store. */
    @Column(nullable = false, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    @JsonIgnore
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseReturnStatus status = PurchaseReturnStatus.DRAFT;

    /** "Tổng tiền hàng trả lại" - the sum of the lines, at the prices the goods are being returned at. */
    @Column(name = "total_goods_value", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalGoodsValue = BigDecimal.ZERO;

    /** "Giảm giá" - what the supplier withholds from the refund (restocking, damage, an agreed deduction). */
    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /**
     * "Nhà cung cấp cần trả" = totalGoodsValue - discountAmount: the gross
     * obligation the other way round, BEFORE anything they have already
     * handed back (amountReceived). Persisted rather than view-computed for
     * the same reason as PurchaseOrder#payableAmount.
     */
    @Column(name = "refund_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;

    /** "Nhà cung cấp đã trả" - cash/transfer refunded at return time; the rest comes off the running debt instead. */
    @Column(name = "amount_received", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal amountReceived = BigDecimal.ZERO;

    @Column(length = 1000)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @JsonIgnore
    private User createdBy;

    /** "Người trả" - who clicked "Hoàn thành", set once at completion time; distinct from createdBy. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_id")
    @JsonIgnore
    private User completedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** "Đánh dấu" - the star on the Trả hàng nhập list. A bookmark the shop sets; nothing else reads it. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean starred = false;

    @OneToMany(mappedBy = "purchaseReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseReturnItem> items = new ArrayList<>();

    public void addItem(PurchaseReturnItem item) {
        items.add(item);
        item.setPurchaseReturn(this);
    }

    public void clearItems() {
        items.clear();
    }
}
