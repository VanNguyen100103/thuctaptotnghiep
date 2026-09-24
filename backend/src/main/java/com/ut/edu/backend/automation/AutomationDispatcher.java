package com.ut.edu.backend.automation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
            outbox.markFailed(event.getId(), "n8n responded " + response.getStatusCode());
            return false;
        } catch (Exception e) {
            // Every failure retries, including a 404. On n8n a 404 means the
            // workflow is not active yet, which is precisely the case a retry
            // is for - the shop activates it and the backlog goes through.
            outbox.markFailed(event.getId(), e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
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
