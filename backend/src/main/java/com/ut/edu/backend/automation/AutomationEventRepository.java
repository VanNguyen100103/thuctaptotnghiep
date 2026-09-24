package com.ut.edu.backend.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AutomationEventRepository extends JpaRepository<AutomationEvent, Long> {

    /**
     * The rows this sweeper may take, locked against the other instances.
     *
     * FOR UPDATE SKIP LOCKED is what makes running three backend replicas
     * (docker-compose does, by default) safe: each sweeper walks past rows
     * another one is already holding instead of blocking on them or, worse,
     * reading them and sending the same webhook twice. Ids only, because the
     * lock is released the moment the claiming transaction commits and the
     * entities would be stale by the time they were posted anyway.
     */
    @Query(value = """
            SELECT id FROM automation_events
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AutomationEvent e SET e.status = :sending, e.claimedAt = :now WHERE e.id IN :ids")
    int claim(@Param("ids") List<Long> ids,
              @Param("sending") AutomationEventStatus sending,
              @Param("now") LocalDateTime now);

    /**
     * Rows an instance claimed and then died holding. Without this they would
     * sit in SENDING forever, which looks exactly like "n8n is slow" and is
     * the one state no retry would ever reach.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AutomationEvent e SET e.status = :pending, e.claimedAt = null "
            + "WHERE e.status = :sending AND e.claimedAt < :staleBefore")
    int releaseStaleClaims(@Param("pending") AutomationEventStatus pending,
                           @Param("sending") AutomationEventStatus sending,
                           @Param("staleBefore") LocalDateTime staleBefore);

    long countByStatus(AutomationEventStatus status);
}
