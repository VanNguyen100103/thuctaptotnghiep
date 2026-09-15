package com.ut.edu.backend.tax;

import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;
import com.ut.edu.backend.user.User;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What the shop has DECIDED about one period's 01/CNKD - not the period
 * itself, and not its figures while they are still moving.
 *
 * A row appears only when there is something to remember: the owner marked
 * the return filed, or typed a note on it. Every period the calendar
 * produces is listed with or without one (see {@link TaxPeriod}), so an
 * untouched year needs no rows at all and no seeding job to create them.
 *
 * The money columns are a SNAPSHOT taken at the moment of filing, deliberately
 * not a live total. Once a return has gone to the tax office, what the screen
 * must show is what was sent - a sale corrected next week would otherwise
 * silently rewrite a filed quarter, and the shop would have no way to see that
 * its books and its filing had drifted apart. While a period is unfiled these
 * stay null and the figures are recomputed from invoices on every read.
 */
@Entity
@Table(name = "tax_declarations", indexes = {
    @Index(name = "idx_tax_declarations_store", columnList = "store_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_tax_declarations_store_period",
                      columnNames = {"store_id", "period_year", "period_type", "period_number"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "submittedBy"})
public class TaxDeclaration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    /**
     * Part of the row's identity, not just a label: a shop that switches from
     * quarterly to monthly filing mid-year has both Quý 1 and Tháng 1 of the
     * same year, and they are different returns.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    @Builder.Default
    private TaxPeriodType periodType = TaxPeriodType.QUARTER;

    /** 1-4 for a quarter, 1-12 for a month. */
    @Column(name = "period_number", nullable = false)
    private Integer periodNumber;

    /**
     * "Lần kê khai" - 1 is "Lần đầu", anything higher is "Bổ sung lần N".
     * Bumped when a filed return is reopened and filed again, which is what
     * the tax office calls a tờ khai bổ sung.
     */
    @Column(name = "declaration_round", nullable = false)
    @Builder.Default
    private Integer declarationRound = 1;

    /** Null until the owner marks the return filed; the single stored fact behind TaxDeclarationStatus. */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id")
    @JsonIgnore
    private User submittedBy;

    /** "Doanh thu tính thuế" as filed. Null while unfiled - see the class comment. */
    @Column(name = "taxable_revenue", precision = 14, scale = 2)
    private BigDecimal taxableRevenue;

    @Column(name = "vat_amount", precision = 14, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "pit_amount", precision = 14, scale = 2)
    private BigDecimal pitAmount;

    /** Which rate pair the filed figures were computed with, so a later change in Thiết lập cannot restate them. */
    @Enumerated(EnumType.STRING)
    @Column(name = "activity", length = 30)
    private TaxActivity activity;

    @Column(length = 1000)
    private String note;

    public boolean isSubmitted() {
        return submittedAt != null;
    }
}
