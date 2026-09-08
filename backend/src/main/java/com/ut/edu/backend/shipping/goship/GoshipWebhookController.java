package com.ut.edu.backend.shipping.goship;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Receives Goship's shipment-status push. Registered in their dashboard
 * under Tài khoản > Developer > Quản lý Goship Webhooks, one endpoint per
 * event.
 *
 * Goship resends anything that does not answer 200, three minutes apart, so
 * a shipment we do not recognise still answers 200 - it is not an error for
 * a webhook on this account to be about somebody else's order, and retrying
 * would never make it ours. A bad signature is the exception: that answers
 * 401 on purpose, so a misconfigured secret announces itself through their
 * retries instead of failing silently the way an accepted-and-ignored
 * webhook would.
 *
 * Note the sandbox does not fire these at all - Goship's own docs say to
 * simulate them by hand - so this path only comes alive in production.
 *
 * POST /api/goship/webhook - permitAll (see SecurityConfig).
 */
@RestController
@RequestMapping("/goship")
@RequiredArgsConstructor
@Slf4j
public class GoshipWebhookController {

    private final GoshipShipmentService shipmentService;
    private final GoshipSignatureService signatureService;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "x-goship-hmac-sha256", required = false) String signature) {

        JsonNode payload;
        String canonicalJson;
        try {
            payload = objectMapper.readTree(rawPayload);
            // Goship's reference implementations sign a re-serialized copy of
            // the body rather than the bytes they sent, so the verifier needs
            // both forms - see GoshipSignatureService.
            canonicalJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Goship webhook body was not valid JSON", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Malformed payload"));
        }

        if (!signatureService.verify(rawPayload, canonicalJson, signature)) {
            log.error("Goship webhook rejected: {}", rejectionReason(signature));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid signature"));
        }

        try {
            shipmentService.handleWebhook(payload);
        } catch (Exception e) {
            // Answer 200 anyway: a bug on this side is not something Goship
            // can fix by sending the same thing again every three minutes.
            log.error("Failed to process Goship webhook: {}", rawPayload, e);
        }
        return ResponseEntity.ok().build();
    }

    private String rejectionReason(String signature) {
        if (!signatureService.isConfigured()) {
            return "GOSHIP_CLIENT_SECRET is not set, so no webhook can ever be accepted";
        }
        if (signature == null || signature.isBlank()) {
            return "the request carried no x-goship-hmac-sha256 header";
        }
        return "the signature does not match GOSHIP_CLIENT_SECRET";
    }
}
