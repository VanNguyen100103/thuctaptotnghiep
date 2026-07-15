package com.ut.edu.backend.store;

import com.ut.edu.backend.common.BaseEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Subscription of a store to a SaaS plan.
 * A store has at most one ACTIVE subscription at a time; history is kept as rows.
 */
@Entity
@Table(name = "subscriptions", indexes = {
    @Index(name = "idx_subscription_store", columnList = "store_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class Subscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    @JsonIgnore
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionPlan plan = SubscriptionPlan.FREE_TRIAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDate startDate;

    /**
     * Null for open-ended subscriptions (e.g. PayPal recurring until cancelled)
     */
    private LocalDate endDate;

    @Column(length = 50)
    private String paypalSubscriptionId;

    public boolean isCurrentlyActive() {
        return status == SubscriptionStatus.ACTIVE
                && (endDate == null || !endDate.isBefore(LocalDate.now()));
    }
}
