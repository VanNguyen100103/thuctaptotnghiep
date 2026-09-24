package com.ut.edu.backend.automation;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The one way business code raises an automation event.
 *
 * Callers get a single line and no new failure mode: this writes a row and
 * returns. It deliberately does <em>not</em> call n8n - see V43 for why the
 * hop is asynchronous - and it is deliberately not annotated
 * {@code @Transactional}, so the insert joins the caller's transaction. That
 * is the whole point of an outbox: a sale that rolls back takes its
 * "sale.completed" event with it, and a sale that commits cannot possibly
 * fail to have announced itself.
 *
 * What it will not do is take a shop's checkout down with it. Building the
 * payload is done inside a guard, because a notification is by design the
 * losable part of the transaction - AutomationEvents explains which events
 * qualify, and none of them are allowed to be the reason a customer cannot
 * pay.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationEventPublisher {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AutomationEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Off by default. A deployment with no n8n behind it should not be
     * accumulating rows nobody will ever read, so the publisher stops at the
     * front door rather than filling a table the dispatcher is not draining.
     */
    @Value("${automation.enabled:false}")
    private boolean enabled;

    /**
     * @param eventKey one of {@link AutomationEvents}
     * @param storeId  whose event this is - the only routing n8n gets, and the
     *                 reason a workflow can be written once and serve every shop
     * @param data     the event body, under "data" in the envelope; values must
     *                 be things Jackson can write without help (numbers,
     *                 strings, BigDecimal, lists, maps)
     */
    public void publish(String eventKey, Long storeId, Map<String, Object> data) {
        if (!enabled) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope(eventId, eventKey, storeId, data));
        } catch (Exception e) {
            log.error("Could not serialize automation event {} for store {} - event dropped", eventKey, storeId, e);
            return;
        }

        AutomationEvent event = AutomationEvent.builder()
                .storeId(storeId)
                .eventId(eventId)
                .eventKey(eventKey)
                .payload(payload)
                .status(AutomationEventStatus.PENDING)
                .attempts(0)
                // Due immediately; the sweeper is what decides when it actually goes.
                .nextAttemptAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
        log.debug("Queued automation event {} ({}) for store {}", eventKey, eventId, storeId);
    }

    /**
     * The shape every workflow can rely on. Kept flat and boring: the envelope
     * is what n8n's router branches on, "data" is the only part that differs
     * between event kinds, so a workflow written for one event is not broken
     * by a field added to another.
     */
    private Map<String, Object> envelope(UUID eventId, String eventKey, Long storeId, Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("event", eventKey);
        envelope.put("storeId", storeId);
        envelope.put("occurredAt", LocalDateTime.now().format(TIMESTAMP));
        envelope.put("data", data);
        return envelope;
    }
}
