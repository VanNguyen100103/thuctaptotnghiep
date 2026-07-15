package com.ut.edu.backend.store;

import com.ut.edu.backend.order.OrderRepository;
import com.ut.edu.backend.order.OrderStatus;
import com.ut.edu.backend.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform Store Management (SaaS operator)
 * Restricted to SUPER_ADMIN: list every store, suspend/reactivate stores,
 * platform-wide statistics. SUPER_ADMIN carries no storeId, so TenantContext
 * stays empty and the Hibernate tenant filter is disabled -> queries here see
 * data across all stores by design.
 */
@RestController
@RequestMapping("/platform/stores")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class PlatformStoreController {

    private final StoreRepository storeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    /**
     * List all stores with pagination
     * GET /api/platform/stores?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<?> getAllStores(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Page<Store> stores = storeRepository.findAll(PageRequest.of(page, size, sort));

        Map<String, Object> response = new HashMap<>();
        response.put("stores", stores.getContent());
        response.put("currentPage", stores.getNumber());
        response.put("totalItems", stores.getTotalElements());
        response.put("totalPages", stores.getTotalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * Get one store with its subscriptions
     * GET /api/platform/stores/{storeId}
     */
    @GetMapping("/{storeId}")
    public ResponseEntity<?> getStore(@PathVariable Long storeId) {
        return storeRepository.findById(storeId)
                .<ResponseEntity<?>>map(store -> ResponseEntity.ok(Map.of(
                        "store", store,
                        "subscriptions", subscriptionRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Store not found: " + storeId)));
    }

    /**
     * Suspend / reactivate a store
     * PATCH /api/platform/stores/{storeId}/status  body: {"status": "SUSPENDED"}
     */
    @PatchMapping("/{storeId}/status")
    public ResponseEntity<?> updateStoreStatus(
            @PathVariable Long storeId,
            @RequestBody Map<String, String> request) {
        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Store not found: " + storeId));
        }

        String statusStr = request.get("status");
        StoreStatus newStatus;
        try {
            newStatus = StoreStatus.valueOf(statusStr != null ? statusStr.toUpperCase() : "");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "status must be one of TRIAL, ACTIVE, SUSPENDED"));
        }

        StoreStatus oldStatus = store.getStatus();
        store.setStatus(newStatus);
        storeRepository.save(store);
        log.warn("Store {} ({}) status changed {} -> {} by platform admin",
                store.getId(), store.getSlug(), oldStatus, newStatus);

        return ResponseEntity.ok(Map.of(
                "message", "Store status updated",
                "storeId", store.getId(),
                "oldStatus", oldStatus,
                "newStatus", newStatus
        ));
    }

    /**
     * Platform-wide statistics
     * GET /api/platform/stores/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getPlatformStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStores", storeRepository.count());
        stats.put("totalUsers", userRepository.count());
        stats.put("totalOrders", orderRepository.count());

        BigDecimal totalRevenue = orderRepository.sumTotalByStatusIn(
                List.of(OrderStatus.DELIVERED, OrderStatus.PAID));
        stats.put("totalRevenue", totalRevenue);
        return ResponseEntity.ok(stats);
    }
}
