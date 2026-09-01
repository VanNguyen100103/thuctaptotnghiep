package com.ut.edu.backend.store;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Store owner's subscription self-service: subscribe, cancel, check status.
 * OWNER only - matches StoreStaffController's shape.
 */
@RestController
@RequestMapping("/store/subscription")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
@Slf4j
public class StoreSubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantGuard tenantGuard;

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@Valid @RequestBody SubscribeRequest request) {
        if (request.getPlan() == SubscriptionPlan.FREE_TRIAL) {
            return ResponseEntity.badRequest().body(Map.of("error", "FREE_TRIAL isn't purchasable"));
        }
        String approveUrl = subscriptionService.createSubscription(tenantGuard.requireStore(), request.getPlan());
        return ResponseEntity.ok(Map.of("approveUrl", approveUrl));
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancel() {
        subscriptionService.cancelSubscription(tenantGuard.requireStore());
        return ResponseEntity.ok(Map.of("message", "Cancellation requested"));
    }

    @GetMapping
    public ResponseEntity<?> current() {
        Optional<Subscription> subscription = subscriptionRepository
                .findFirstByStoreIdAndStatusOrderByStartDateDesc(tenantGuard.requireStore(), SubscriptionStatus.ACTIVE);
        return subscription
                .map(s -> ResponseEntity.ok(Map.of(
                        "plan", s.getPlan(),
                        "status", s.getStatus(),
                        "startDate", s.getStartDate(),
                        "endDate", s.getEndDate() == null ? "" : s.getEndDate())))
                .orElseGet(() -> ResponseEntity.ok(Map.of("plan", "NONE")));
    }
}
