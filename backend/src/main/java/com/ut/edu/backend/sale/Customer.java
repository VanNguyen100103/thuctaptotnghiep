package com.ut.edu.backend.sale;

import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

/**
 * A walk-in / in-store customer captured from the "Bán hàng" (POS) quick-add
 * modal. Deliberately NOT the same as {@link com.ut.edu.backend.user.User} -
 * this is a plain contact record with no login, matching how KiotViet's own
 * "Thêm khách hàng" works at the register (only a name is required; nothing
 * here ever authenticates). Region/ward/group follow the same
 * plain-text-no-reference-table convention as Supplier's own fields.
 */
@Entity
@Table(name = "customers", indexes = {
    @Index(name = "idx_customers_store", columnList = "store_id"),
    @Index(name = "idx_customers_name", columnList = "name")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_customers_store_code", columnNames = {"store_id", "code"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store"})
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    /** Auto-generated "Mã khách hàng" - KH000001, KH000002, ... per store. */
    @NotBlank
    @Column(nullable = false, length = 30)
    private String code;

    @NotBlank(message = "Customer name is required")
    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 200)
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** "Nam" / "Nữ" - free text, same treatment as Product#gender. */
    @Column(length = 10)
    private String gender;

    @Column(length = 500)
    private String address;

    /** "Khu vực" (Tỉnh/Thành phố - Quận/Huyện) - plain text, no real province/district dataset behind it, same as Supplier#region. */
    @Column(length = 200)
    private String region;

    /** "Phường/Xã" - plain text, same reasoning as {@link #region}. */
    @Column(length = 200)
    private String ward;

    /** "Nhóm khách hàng" - a free-text tag, same treatment as Supplier#groupName. */
    @Column(name = "group_name", length = 200)
    private String groupName;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** "Điểm" - loyalty point balance, earned from Sale checkout (see SaleService) and redeemable there 1 point = 1,000 VND. */
    @Column(name = "loyalty_points", nullable = false)
    @Builder.Default
    private Integer loyaltyPoints = 0;
}
