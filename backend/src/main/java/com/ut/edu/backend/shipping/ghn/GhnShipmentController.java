package com.ut.edu.backend.shipping.ghn;

import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.TenantGuard;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Backs the "+ Tạo đơn test" form and shipment table on Đối tác giao hàng >
 * Tích hợp > Lịch sử giao hàng - see GhnShipment's doc comment for scope.
 */
@RestController
@RequestMapping("/store/ghn")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class GhnShipmentController {

    private final GhnShipmentService ghnShipmentService;
    private final TenantGuard tenantGuard;
    private final AuthorizationService authorizationService;

    @GetMapping("/provinces")
    public ResponseEntity<?> provinces() {
        try {
            return ResponseEntity.ok(Map.of("provinces", ghnShipmentService.listProvinces()));
        } catch (GhnApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/districts")
    public ResponseEntity<?> districts(@RequestParam int provinceId) {
        try {
            return ResponseEntity.ok(Map.of("districts", ghnShipmentService.listDistricts(provinceId)));
        } catch (GhnApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/wards")
    public ResponseEntity<?> wards(@RequestParam int districtId) {
        try {
            return ResponseEntity.ok(Map.of("wards", ghnShipmentService.listWards(districtId)));
        } catch (GhnApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/shipments")
    public ResponseEntity<?> list(@RequestParam(required = false) String query, @RequestParam(required = false) String status) {
        return ResponseEntity.ok(Map.of("shipments", ghnShipmentService.list(query, status)));
    }

    @PostMapping("/shipments")
    public ResponseEntity<?> create(@Valid @RequestBody CreateGhnShipmentRequest request) {
        try {
            Long storeId = tenantGuard.requireStore();
            GhnShipment saved = ghnShipmentService.createShipment(storeId, authorizationService.getCurrentUser(), request);
            log.info("GHN test shipment created: {} (order_code {})", saved.getClientOrderCode(), saved.getGhnOrderCode());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Đã tạo đơn GHN", "shipment", saved));
        } catch (GhnApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create GHN shipment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to create shipment"));
        }
    }

    @PatchMapping("/shipments/{id}/refresh")
    public ResponseEntity<?> refresh(@PathVariable Long id) {
        try {
            GhnShipment saved = ghnShipmentService.refreshStatus(id);
            return ResponseEntity.ok(Map.of("shipment", saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (GhnApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }
}
