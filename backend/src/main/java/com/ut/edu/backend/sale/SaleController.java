package com.ut.edu.backend.sale;

import com.ut.edu.backend.exception.SubscriptionRequiredException;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * "Bán hàng" - the POS register. See SaleService for the checkout rules.
 */
@RestController
@RequestMapping("/store/sales")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@Slf4j
public class SaleController {

    @Autowired
    private SaleService saleService;

    @Autowired
    private TenantGuard tenantGuard;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    @Autowired
    private AuthorizationService authorizationService;

    /** POST /api/store/sales - "Thanh toán": finalizes the sale immediately (no draft step). */
    @PostMapping
    public ResponseEntity<?> checkout(@Valid @RequestBody CreateSaleRequest request) {
        try {
            Long storeId = tenantGuard.requireStore();
            subscriptionGuard.requireActiveSubscription(storeId);
            User cashier = authorizationService.getCurrentUser();
            Sale saved = saleService.checkout(storeId, cashier, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Thanh toán thành công",
                    "sale", SaleResponse.from(saved)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to checkout sale", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete sale"));
        }
    }
}
