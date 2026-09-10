package com.ut.edu.backend.shipping.goship;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Asks Goship where each parcel still in flight has got to, and lets the
 * answer move the order.
 *
 * The webhook was meant to make this unnecessary, and in production it mostly
 * does. It is not enough on its own:
 *   - the sandbox does not fire webhooks at all, so a status changed on
 *     Goship's own dashboard reaches this system by no other route;
 *   - a webhook has to be registered against a public URL, which a shop can
 *     forget, and Goship gives up after three failed deliveries;
 *   - the manual refresh is one parcel at a time, on a screen nobody watches.
 *
 * So the webhook stays the fast path and this is the floor under it. Both end
 * up in the same place ({@link GoshipShipmentService#refreshStatus}), and
 * applying the same status twice changes nothing.
 *
 * Runs without a tenant: the Hibernate tenant filter is enabled per request,
 * and there is no request here, so this sees every store's parcels - which is
 * what a sweep needs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoshipStatusPollJob {

    private final ShipmentRepository shipmentRepository;
    private final GoshipShipmentService shipmentService;
    private final GoshipClient goshipClient;

    /**
     * How far back to keep asking. A parcel that has not reached a final status
     * in this long is not going to; without a floor the sweep would grow by one
     * doomed shipment a month, forever.
     */
    @Value("${goship.poll.max-age-days:30}")
    private int maxAgeDays;

    /** A ceiling on one sweep, so a backlog cannot turn into a burst of API calls. */
    @Value("${goship.poll.batch-size:50}")
    private int batchSize;

    @Value("${goship.poll.enabled:true}")
    private boolean enabled;

    /** Every 10 minutes. A parcel does not move faster than that, and Goship is rate-limited. */
    @Scheduled(fixedDelayString = "${goship.poll.interval-ms:600000}", initialDelayString = "${goship.poll.initial-delay-ms:60000}")
    public void run() {
        if (!enabled || !goshipClient.isConfigured()) {
            return;
        }
        pollOnce();
    }

    /** The scheduled body, separated so it can be called without waiting on a timer. */
    public int pollOnce() {
        List<Shipment> inFlight = shipmentRepository.findInFlight(
                GoshipShipmentStatus.finalCodes(),
                LocalDateTime.now().minusDays(maxAgeDays),
                org.springframework.data.domain.PageRequest.of(0, batchSize));
        if (inFlight.isEmpty()) {
            return 0;
        }

        int moved = 0;
        for (Shipment shipment : inFlight) {
            try {
                // One parcel failing - a booking Goship has never heard of, a
                // timeout - must not end the sweep for the rest.
                shipmentService.refreshStatus(shipment);
                moved++;
            } catch (Exception e) {
                log.warn("Goship poll failed for shipment {} ({}): {}",
                        shipment.getId(), shipment.getOrderRef(), e.getMessage());
            }
        }
        log.info("Goship poll: refreshed {} of {} in-flight shipment(s)", moved, inFlight.size());
        return moved;
    }
}
