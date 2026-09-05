package com.ut.edu.backend.shipping.ghn;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives GHN's order-status push. Unlike SePay/PayPal, GHN does not
 * self-serve a webhook URL in the merchant dashboard - it has to be emailed
 * to api@ghn.vn (ClientID/URL/environment) and configured on their side, so
 * this may sit unused until that's done; GhnShipmentController's manual
 * "refresh" endpoint is the fallback in the meantime (see application.
 * properties APP_BACKEND_URL for the public URL to send GHN).
 *
 * No signature verification - GHN's webhook docs don't document one (unlike
 * SePay's HMAC), so this only trusts a matching, already-known order_code;
 * an unrecognized one is logged and ignored rather than acted on.
 *
 * POST /api/ghn/webhook - permitAll (see SecurityConfig), always returns 200
 * so GHN doesn't infinitely retry (10 attempts, 5s apart, per their docs).
 */
@RestController
@RequestMapping("/ghn")
@RequiredArgsConstructor
@Slf4j
public class GhnWebhookController {

    private final GhnShipmentService ghnShipmentService;

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody JsonNode payload) {
        try {
            ghnShipmentService.handleWebhook(payload);
        } catch (Exception e) {
            log.error("Failed to process GHN webhook: {}", payload, e);
        }
        return ResponseEntity.ok().build();
    }
}
