package com.ut.edu.backend.purchasereturn;

import com.ut.edu.backend.product.Product;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One line of a Trả hàng nhập document. productName/productSku are
 * snapshotted at creation time for the same reason PurchaseOrderItem does
 * it: the paperwork has to keep reading correctly after the product is
 * renamed or deleted from the catalog.
 */
@Entity
@Table(name = "purchase_return_items", indexes = {
    @Index(name = "idx_purchase_return_items_return", columnList = "purchase_return_id"),
    @Index(name = "idx_purchase_return_items_product", columnList = "product_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"purchaseReturn", "product"})
public class PurchaseReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_return_id", nullable = false)
    @JsonIgnore
    private PurchaseReturn purchaseReturn;

    /** Null once the store deletes the product - the snapshot fields below keep the record intact. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @JsonIgnore
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_sku", nullable = false, length = 100)
    private String productSku;

    @Column(nullable = false)
    private Integer quantity;

    /** The price the goods go back at - defaults to what they came in at, but the shop can agree another figure. */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** = quantity * unitPrice - discountAmount, computed server-side. */
    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal lineTotal = BigDecimal.ZERO;
}
