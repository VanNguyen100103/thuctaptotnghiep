package com.ut.edu.backend.tax;

import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * "Thiết lập" under Thuế & Kế toán - who the household business is to the tax
 * office, and the two facts that decide what its returns look like: which
 * activity group it registered under, and whether it is on kê khai or khoán.
 *
 * Separate from {@link Store} rather than more columns on it, because these
 * are the taxpayer's identity, not the shop's: the MST and the registered
 * name belong to the person who owns the business, they are edited on a
 * different screen by a different pair of eyes, and a store that never opens
 * this module should not carry half-filled tax fields around. One row per
 * store, created on first read so the screen always has something to show.
 */
@Entity
@Table(name = "tax_profiles", uniqueConstraints = {
    @UniqueConstraint(name = "uk_tax_profiles_store", columnNames = {"store_id"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store"})
public class TaxProfile extends BaseEntity {

    /**
     * "Doanh thu không chịu thuế" - Điều 5, Luật Thuế GTGT 48/2024, in force
     * from 01/01/2026: a household business under 200 triệu of annual revenue
     * owes neither GTGT nor TNCN.
     *
     * A default rather than a constant because it is a number the National
     * Assembly moves (100 triệu until the end of 2025), and a shop filing for
     * an earlier year needs the figure that applied then.
     */
    public static final BigDecimal DEFAULT_EXEMPT_THRESHOLD = new BigDecimal("200000000");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    /** "Tên hộ kinh doanh" as registered - not necessarily the shop sign, which is Store#name. */
    @Column(name = "business_name", length = 200)
    private String businessName;

    /** "Mã số thuế" - 10 digits for the household, 13 with a branch suffix. Free text: validation belongs on the form, not here. */
    @Column(name = "tax_code", length = 20)
    private String taxCode;

    /** "Người đại diện hộ kinh doanh" - the individual whose TNCN this ultimately is. */
    @Column(name = "owner_name", length = 200)
    private String ownerName;

    /** "Địa chỉ kinh doanh" as it must appear on the return. */
    @Column(name = "business_address", length = 255)
    private String businessAddress;

    /** "Cơ quan thuế quản lý" - the managing tax office, printed on the form's header. */
    @Column(name = "tax_office", length = 200)
    private String taxOffice;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_method", nullable = false, length = 20)
    @Builder.Default
    private TaxMethod taxMethod = TaxMethod.KE_KHAI;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    @Builder.Default
    private TaxPeriodType periodType = TaxPeriodType.QUARTER;

    /**
     * "Ngành nghề kinh doanh" the shop's revenue is declared under, which is
     * what picks the pair of rates in {@link TaxActivity}.
     *
     * One activity for the whole shop, for now: splitting a single invoice
     * across the form's four rows needs a tax group on every product, and
     * inventing one per line from the product's category would put a number
     * on a tax return that nobody chose. A retail store is DISTRIBUTION end
     * to end, which is the case this serves correctly today; per-product
     * groups are the next step, and the detail screen already draws all four
     * rows so they have somewhere to land.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "activity", nullable = false, length = 30)
    @Builder.Default
    private TaxActivity activity = TaxActivity.DISTRIBUTION;

    /** See {@link #DEFAULT_EXEMPT_THRESHOLD}. Zero turns the screen's exemption notice off. */
    @Column(name = "exempt_threshold", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal exemptThreshold = DEFAULT_EXEMPT_THRESHOLD;
}
