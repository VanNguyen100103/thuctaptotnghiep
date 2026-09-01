package com.ut.edu.backend.store;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public store onboarding endpoint.
 * POST /api/stores/register - permitAll (see SecurityConfig); the literal
 * path never clashes with the /stores/{slug} storefront because "register"
 * is a reserved slug.
 */
@RestController
@RequestMapping("/stores")
@RequiredArgsConstructor
@Slf4j
public class StoreOnboardingController {

    private final StoreOnboardingService onboardingService;
    private final StoreStaffService staffService;

    @PostMapping("/register")
    public ResponseEntity<?> registerStore(@Valid @RequestBody RegisterStoreRequest request) {
        log.info("Store registration request: slug={}, owner={}",
                request.getStoreSlug(), request.getUsername());
        try {
            Store store = onboardingService.registerStore(request);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Store registered successfully. Please verify your email with the OTP we sent.",
                    "storeSlug", store.getSlug(),
                    "storeName", store.getName(),
                    "username", request.getUsername(),
                    "trialDays", StoreOnboardingService.TRIAL_DAYS
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Staff member redeems an invitation token and creates their account.
     * POST /api/stores/accept-invite - permitAll ("accept-invite" is a reserved slug)
     */
    @PostMapping("/accept-invite")
    public ResponseEntity<?> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        try {
            StoreStaffService.AcceptInviteResult result = staffService.acceptInvite(request);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Account created. You can now log in.",
                    "username", result.username(),
                    "storeName", result.storeName(),
                    "storeSlug", result.storeSlug(),
                    "storeRole", result.storeRole()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
