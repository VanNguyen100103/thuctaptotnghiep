package com.ut.edu.backend.purchaseorder;

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
 * "Nhập hàng" (goods receipt / purchase order) - the real, working slice of
 * KiotViet's "Mua hàng" tab this app implements. See PurchaseOrderService
 * for the calculation/status-transition rules and TODO.md for what was
 * deliberately left out (Đặt hàng nhập / Trả hàng nhập / Hóa đơn đầu vào,
 * multi-kho, Excel import).
 */
@Entity
@Table(name = "purchase_orders", indexes = {
    @Index(name = "idx_purchase_orders_store", columnList = "store_id"),
    @Index(name = "idx_purchase_orders_status", columnList = "status"),
    @Index(name = "idx_purchase_orders_supplier", columnList = "supplier_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_purchase_orders_store_code", columnNames = {"store_id", "code"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "supplier", "createdBy", "items"})
public class PurchaseOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    /** Auto-generated "Mã nhập hàng" - PN000001, PN000002, ... per store. */
    @Column(nullable = false, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    @JsonIgnore
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    /** "Tổng tiền hàng" - sum of every line's lineTotal. Recomputed server-side on every save, never trusted from the client. */
    @Column(name = "total_goods_value", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalGoodsValue = BigDecimal.ZERO;

    /** "Giảm giá" - order-level discount, subtracted from totalGoodsValue when computing payableAmount. */
    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** "Chi phí nhập trả NCC" - amount already paid to the supplier at receipt time, subtracted from payableAmount. */
    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /**
     * "Chi phí nhập khác" - e.g. shipping, paid to a 3rd party rather than
     * the supplier. Tracked for reference/inventory-cost purposes only -
     * deliberately NOT subtracted from payableAmount (that would understate
     * what's actually owed to the supplier).
     */
    @Column(name = "other_costs", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal otherCosts = BigDecimal.ZERO;

    /** "Cần trả nhà cung cấp" = totalGoodsValue - discountAmount - amountPaid. Persisted (not view-computed) so list/report queries don't need to re-derive it. */
    @Column(name = "payable_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal payableAmount = BigDecimal.ZERO;

    @Column(length = 1000)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @JsonIgnore
    private User createdBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderItem> items = new ArrayList<>();

    public void addItem(PurchaseOrderItem item) {
        items.add(item);
        item.setPurchaseOrder(this);
    }

    public void clearItems() {
        items.clear();
    }
}
