package com.ut.edu.backend.automation;

import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.TenantContext;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One event on its way out to n8n - a row of the outbox described in V43.
 *
 * The store is a plain id, not a {@code @ManyToOne Store}: the sweeper reads
 * these on a scheduled thread with no tenant bound and no session to lazy-load
 * from, and it never needs anything off the store but the number it already
 * puts in the payload.
 */
@Entity
@Table(name = "automation_events", indexes = {
    @Index(name = "idx_automation_events_due", columnList = "status, next_attempt_at"),
    @Index(name = "idx_automation_events_store", columnList = "store_id, created_at")
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class AutomationEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id")
    private Long storeId;

    /** Sent as X-Tryum-Delivery, and stable across retries - the receiver's idempotency key. */
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    /** One of {@link AutomationEvents}. */
    @Column(name = "event_key", nullable = false, length = 60)
    private String eventKey;

    /** The exact JSON body that will be signed and posted. Serialized once, at publish time. */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AutomationEventStatus status = AutomationEventStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** Truncated to fit the column - the full stack trace goes to the log, this is for the screen. */
    @Column(name = "last_error", length = 500)
    private String lastError;
}
