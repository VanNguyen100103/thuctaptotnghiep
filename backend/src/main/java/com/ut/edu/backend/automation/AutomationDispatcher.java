package com.ut.edu.backend.automation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Drains the outbox into n8n.
 *
 * Runs on a timer rather than being triggered by the write, because the
 * interesting cases are all timers: n8n asleep, n8n redeploying, a webhook
 * URL a shop typed wrong three days ago. A sweep that only ran on new events
 * would leave every one of those stuck behind the next sale.
 *
 * Nothing here is allowed to throw. A sweep that dies on one bad event stops
 * delivering every other shop's events too, so each event is posted inside
 * its own guard and the worst any single one can do is spend its attempts.
 *
 * Runs with no tenant bound - the Hibernate tenant filter is enabled per
 * request and there is no request here, so this sees every store's events,
 * which is what a sweep needs (same arrangement as GoshipStatusPollJob).
 */
@Component
@Slf4j
public class AutomationDispatcher {

    /**
     * A claim older than this is assumed to belong to an instance that is
     * gone. Comfortably longer than the read timeout below, so a slow-but-
     * alive POST is never stolen out from under itself and delivered twice.
     */
    private static final Duration STALE_CLAIM_AGE = Duration.ofMinutes(5);

    private final AutomationOutbox outbox;
    private final AutomationSignatureService signatureService;
    private final RestTemplate restTemplate;

    @Value("${automation.enabled:false}")
    private boolean enabled;

    /** The n8n Webhook node's production URL, e.g. https://n8n.example.com/webhook/tryum-events */
    @Value("${automation.webhook-url:}")
    private String webhookUrl;

    /** A ceiling on one sweep, so a backlog cannot turn into a burst of calls at n8n. */
    @Value("${automation.dispatch.batch-size:25}")
    private int batchSize;

    @Autowired
    public AutomationDispatcher(AutomationOutbox outbox,
                                AutomationSignatureService signatureService,
                                RestTemplateBuilder restTemplateBuilder) {
        this(outbox, signatureService, restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                // Deliberately far shorter than the shared RestTemplate's 60s:
                // a webhook that has not answered in 20 seconds is a webhook
                // that is not going to, and the scheduler has other jobs.
                .setReadTimeout(Duration.ofSeconds(20))
                .build());
    }

    /** For tests, which supply their own transport rather than a real one. */
    AutomationDispatcher(AutomationOutbox outbox,
                         AutomationSignatureService signatureService,
                         RestTemplate restTemplate) {
        this.outbox = outbox;
        this.signatureService = signatureService;
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedDelayString = "${automation.dispatch.interval-ms:15000}",
               initialDelayString = "${automation.dispatch.initial-delay-ms:30000}")
    public void run() {
        if (!isConfigured()) {
            return;
        }
        dispatchOnce();
    }

    /**
     * Whether this deployment can send at all. An enabled dispatcher with no
     * URL or no secret would post nothing, or post unsigned; either is worse
     * than being switched off, so it stays quiet instead.
     */
    public boolean isConfigured() {
        return enabled && webhookUrl != null && !webhookUrl.isBlank() && signatureService.isConfigured();
    }

    /** The scheduled body, separated so it can be called without waiting on a timer. */
    public int dispatchOnce() {
        outbox.releaseStaleClaims(STALE_CLAIM_AGE);

        List<AutomationEvent> due = outbox.claimDue(batchSize);
        int delivered = 0;
        for (AutomationEvent event : due) {
            if (deliver(event)) {
                delivered++;
            }
        }
        if (!due.isEmpty()) {
            log.info("Automation sweep: {}/{} event(s) delivered", delivered, due.size());
        }
        return delivered;
    }

    private boolean deliver(AutomationEvent event) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    webhookUrl, HttpMethod.POST, new HttpEntity<>(event.getPayload(), headersFor(event)), String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                outbox.markDelivered(event.getId());
                return true;
            }
            outbox.markFailed(event.getId(), "responded " + response.getStatusCode(),
                    isStillComingUp(response.getStatusCode()));
            return false;

        } catch (ResourceAccessException e) {
            // Nothing answered at all - connect timeout, read timeout, DNS,
            // TLS. On a free-tier receiver this is overwhelmingly "it is
            // asleep and this request is what wakes it", so it does not count
            // against the event's attempts for the first half hour.
            outbox.markFailed(event.getId(), e.getClass().getSimpleName() + ": " + e.getMessage(), true);
            return false;

        } catch (HttpStatusCodeException e) {
            outbox.markFailed(event.getId(), "HTTP " + e.getStatusCode() + ": " + firstLine(e.getResponseBodyAsString()),
                    isStillComingUp(e.getStatusCode()));
            return false;

        } catch (Exception e) {
            outbox.markFailed(event.getId(), e.getClass().getSimpleName() + ": " + e.getMessage(), false);
            return false;
        }
    }

    /**
     * Whether this status means "nothing is listening yet" rather than "what
     * you sent is wrong".
     *
     * 502/503/504 are what a platform proxy returns while the container
     * behind it is still starting, and n8n answers 503 "Database is not
     * ready!" itself for the same stretch. A 404 is deliberately not here:
     * it means the workflow was never published, and no amount of waiting
     * fixes that - the backoff should stretch out so somebody notices.
     */
    private static boolean isStillComingUp(HttpStatusCode status) {
        int code = status.value();
        return code == 502 || code == 503 || code == 504;
    }

    /** Error pages run to kilobytes of HTML; the first line is the part worth storing. */
    private static String firstLine(String body) {
        if (body == null || body.isBlank()) {
            return "(empty body)";
        }
        String line = body.strip().lines().findFirst().orElse("").strip();
        return line.length() <= 200 ? line : line.substring(0, 200);
    }

    /**
     * What n8n needs to trust and route the call. The body is posted exactly
     * as it was stored, because the signature covers those bytes - anything
     * that re-serializes the JSON on the way out breaks verification.
     */
    private HttpHeaders headersFor(AutomationEvent event) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tryum-Event", event.getEventKey());
        headers.set("X-Tryum-Store", String.valueOf(event.getStoreId()));
        headers.set("X-Tryum-Delivery", String.valueOf(event.getEventId()));
        headers.set("X-Tryum-Timestamp", timestamp);
        headers.set("X-Tryum-Signature", signatureService.sign(timestamp, event.getPayload()));
        return headers;
    }
}
