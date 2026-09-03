package com.ut.edu.backend.supplier;

import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.Filter;

/**
 * "Nhà cung cấp" - a supplier a store buys goods from. Backs the supplier
 * search/quick-add on the Nhập hàng (PurchaseOrder) form and its own minimal
 * list page under the "Mua hàng" tab.
 */
@Entity
@Table(name = "suppliers", indexes = {
    @Index(name = "idx_suppliers_store", columnList = "store_id"),
    @Index(name = "idx_suppliers_name", columnList = "name")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_suppliers_store_code", columnNames = {"store_id", "code"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store"})
public class Supplier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    /** Auto-generated "Mã NCC" - NCC000001, NCC000002, ... per store. See PurchaseOrderService#nextSupplierCode. */
    @NotBlank
    @Column(nullable = false, length = 30)
    private String code;

    @NotBlank(message = "Supplier name is required")
    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 200)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
