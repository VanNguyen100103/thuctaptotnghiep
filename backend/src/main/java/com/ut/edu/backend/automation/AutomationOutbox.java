package com.ut.edu.backend.automation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Every database transaction the sweeper needs, and nothing else.
 *
 * Split out from {@link AutomationDispatcher} because the two have opposite
 * requirements: the transactions here must be short, and the POST there takes
 * as long as n8n takes. Holding a row lock across a call to another machine is
 * how a sleeping free-tier container turns into a database full of stuck
 * connections. So a row is claimed and committed, then posted with no
 * transaction open, then its outcome written in a second short one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationOutbox {

    /**
     * How long to wait before each retry, in minutes: a minute, five, half an
     * hour, two hours, six. Front-loaded because the common failure is n8n
     * being briefly asleep or restarting, which a minute fixes; stretched at
     * the end because whatever is still failing after two hours is a
     * misconfiguration that needs a person, and hammering it helps nobody.
     * Together they give a shop most of a working day to notice and fix a
     * broken webhook URL before anything is given up on.
     */
    private static final int[] BACKOFF_MINUTES = {1, 5, 30, 120, 360};

    private final AutomationEventRepository eventRepository;

    @Value("${automation.dispatch.max-attempts:6}")
    private int maxAttempts;

    /**
     * Takes up to {@code limit} due events and marks them SENDING, so no other
     * instance will pick them up. Returns them already claimed - the caller
     * only has to post them.
     */
    @Transactional
    public List<AutomationEvent> claimDue(int limit) {
        List<Long> ids = eventRepository.findDueIds(LocalDateTime.now(), limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        eventRepository.claim(ids, AutomationEventStatus.SENDING, LocalDateTime.now());
        return eventRepository.findAllById(ids);
    }

    @Transactional
    public void markDelivered(Long eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(AutomationEventStatus.DELIVERED);
            event.setAttempts(event.getAttempts() + 1);
            event.setDeliveredAt(LocalDateTime.now());
            event.setLastError(null);
            eventRepository.save(event);
        });
    }

    /**
     * Records a failed attempt and decides whether there will be another one.
     *
     * The reason is kept on the row rather than only in the log, because the
     * person who needs it is a shop owner looking at a screen that says an
     * alert never arrived, not whoever can read the server's stdout.
     */
    @Transactional
    public void markFailed(Long eventId, String reason) {
        eventRepository.findById(eventId).ifPresent(event -> {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setClaimedAt(null);
            event.setLastError(truncate(reason));

            if (attempts >= maxAttempts) {
                event.setStatus(AutomationEventStatus.DEAD);
                log.warn("Automation event {} ({}) gave up after {} attempts: {}",
                        event.getEventId(), event.getEventKey(), attempts, reason);
            } else {
                int minutes = BACKOFF_MINUTES[Math.min(attempts - 1, BACKOFF_MINUTES.length - 1)];
                event.setStatus(AutomationEventStatus.PENDING);
                event.setNextAttemptAt(LocalDateTime.now().plusMinutes(minutes));
                log.info("Automation event {} ({}) attempt {} failed, retrying in {}m: {}",
                        event.getEventId(), event.getEventKey(), attempts, minutes, reason);
            }
            eventRepository.save(event);
        });
    }

    /** Puts back anything left SENDING by an instance that died mid-POST. */
    @Transactional
    public int releaseStaleClaims(Duration olderThan) {
        int released = eventRepository.releaseStaleClaims(
                AutomationEventStatus.PENDING,
                AutomationEventStatus.SENDING,
                LocalDateTime.now().minus(olderThan));
        if (released > 0) {
            log.warn("Released {} automation event(s) left in SENDING by a previous run", released);
        }
        return released;
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 497) + "...";
    }
}
