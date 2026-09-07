package com.ut.edu.backend.payment;

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
 * One "Chuyển khoản" QR shown at the POS counter, waiting for the customer's
 * transfer. See the V27 migration for why a POS QR needs a row of its own
 * where the storefront's QR does not.
 *
 * The tenant filter here scopes what the cashier can read back. The SePay
 * webhook looks a session up by {@code reference} on a request with no
 * authentication and therefore no TenantContext, so the filter is off on
 * that path (same as it already is for the order lookup next to it) - which
 * is exactly what it needs, since the bank account is shared app-wide and
 * the incoming transfer says nothing about which store it belongs to.
 */
@Entity
@Table(name = "pos_payment_sessions", indexes = {
    @Index(name = "idx_pos_payment_sessions_store", columnList = "store_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_pos_payment_sessions_reference", columnNames = {"reference"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "createdBy"})
public class PosPaymentSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    /** Transfer content embedded in the QR ("POS" + 9 digits) - the only thing tying an incoming transfer back to this counter. */
    @Column(nullable = false, length = 32)
    private String reference;

    /** What the customer was asked to transfer. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PosPaymentSessionStatus status;

    /** What actually arrived - an over-payment is accepted and recorded here as-is. */
    @Column(name = "transferred_amount", precision = 14, scale = 2)
    private BigDecimal transferredAmount;

    /** SePay's own transaction id, so a PAID row can be found again in their dashboard. */
    @Column(name = "sepay_transaction_id", length = 64)
    private String sepayTransactionId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** When the terminal should give up polling and tell the cashier to check manually - not a deadline for the money itself. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** The cashier who showed the QR. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @JsonIgnore
    private User createdBy;
}
