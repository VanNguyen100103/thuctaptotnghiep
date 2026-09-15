package com.ut.edu.backend.salereturn;

import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.sale.SaleItem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One line of a "Trả hàng" document. productName/productSku are snapshotted
 * for the same reason SaleItem snapshots them - the paperwork has to keep
 * reading correctly after the product is renamed or deleted from the catalog.
 *
 * {@link #saleItem} is the one link here that is NOT a snapshot: it is what
 * caps a return at what that invoice line actually sold, across however many
 * returns the invoice has already had.
 */
@Entity
@Table(name = "sale_return_items", indexes = {
    @Index(name = "idx_sale_return_items_return", columnList = "sale_return_id"),
    @Index(name = "idx_sale_return_items_sale_item", columnList = "sale_item_id"),
    @Index(name = "idx_sale_return_items_product", columnList = "product_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"saleReturn", "saleItem", "product"})
public class SaleReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_return_id", nullable = false)
    @JsonIgnore
    private SaleReturn saleReturn;

    /** The invoice line these units came off. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false)
    @JsonIgnore
    private SaleItem saleItem;

    /** Null once the store deletes the product - the snapshots below keep the record intact. */
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

    /** Taken off the invoice line, never off the catalog: a refund is at what the customer paid. */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** This line's share of the invoice line's own discount, prorated by how many units came back. */
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** = quantity * unitPrice - discountAmount, computed server-side. */
    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal lineTotal = BigDecimal.ZERO;
}
