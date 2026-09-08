package com.ut.edu.backend.shipping.goship;

import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.Store;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * The shipping half of the dashboard: address lookups for the delivery
 * form, the carrier price comparison, and the shipment list.
 *
 * Replaced the GHN-specific controller, so the path is carrier-neutral -
 * which carrier a shipment goes to is now a per-order choice rather than a
 * property of the integration.
 */
@RestController
@RequestMapping("/store/shipping")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class GoshipShipmentController {

    private final GoshipShipmentService shipmentService;
    private final AuthorizationService authorizationService;

    /**
     * Where this store's parcels are collected. Lives on the store, not in
     * deployment config, because every shop on the platform ships from its
     * own counter - so it has to be editable, and this is where the Đối tác
     * giao hàng screen reads and writes it.
     *
     * Answers even when nothing is set: the form exists to fill it in.
     */
    @GetMapping("/pickup")
    public ResponseEntity<?> pickup() {
        Store store = shipmentService.currentStore();
        return ResponseEntity.ok(pickupBody(store));
    }

    @PutMapping("/pickup")
    public ResponseEntity<?> savePickup(@Valid @RequestBody SavePickupAddressRequest request) {
        Store store = shipmentService.savePickupAddress(
                request.cityId().trim(), request.districtId().trim(), request.wardId().trim());
        log.info("Store {} pickup point set to city={} district={} ward={}",
                store.getId(), store.getGoshipCityId(), store.getGoshipDistrictId(), store.getGoshipWardId());
        return ResponseEntity.ok(pickupBody(store));
    }

    /**
     * The contact half is read-only here on purpose - it is the store's own
     * name, phone and address, edited where a store profile is edited, and
     * duplicating it into a shipping form would give two places to change it
     * and one of them would go stale.
     */
    private Map<String, Object> pickupBody(Store store) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", store.getName() != null ? store.getName() : "");
        body.put("phone", store.getPhone() != null ? store.getPhone() : "");
        body.put("street", store.getAddress() != null ? store.getAddress() : "");
        body.put("cityId", store.getGoshipCityId());
        body.put("districtId", store.getGoshipDistrictId());
        body.put("wardId", store.getGoshipWardId());
        return body;
    }

    @GetMapping("/cities")
    public ResponseEntity<?> cities() {
        try {
            return ResponseEntity.ok(Map.of("cities", shipmentService.listCities()));
        } catch (GoshipApiException e) {
            return upstream(e);
        }
    }

    @GetMapping("/districts")
    public ResponseEntity<?> districts(@RequestParam String cityId) {
        try {
            return ResponseEntity.ok(Map.of("districts", shipmentService.listDistricts(cityId)));
        } catch (GoshipApiException e) {
            return upstream(e);
        }
    }

    @GetMapping("/wards")
    public ResponseEntity<?> wards(@RequestParam String districtId) {
        try {
            return ResponseEntity.ok(Map.of("wards", shipmentService.listWards(districtId)));
        } catch (GoshipApiException e) {
            return upstream(e);
        }
    }

    /**
     * POST rather than GET despite reading nothing: the parcel and both
     * addresses are a structured body, and Goship prices from all of it.
     */
    @PostMapping("/rates")
    public ResponseEntity<?> rates(@Valid @RequestBody RateQuoteRequest request) {
        try {
            return ResponseEntity.ok(Map.of("rates", shipmentService.quote(request)));
        } catch (GoshipApiException e) {
            return upstream(e);
        }
    }

    @GetMapping("/shipments")
    public ResponseEntity<?> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer status) {
        return ResponseEntity.ok(Map.of("shipments", shipmentService.list(query, status)));
    }

    @PostMapping("/shipments")
    public ResponseEntity<?> create(@Valid @RequestBody CreateShipmentRequest request) {
        try {
            Shipment saved = shipmentService.createShipment(authorizationService.getCurrentUser(), request);
            log.info("Goship shipment booked: {} via {} (goship id {})",
                    saved.getOrderRef(), saved.getCarrierShortName(), saved.getGoshipId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Đã tạo vận đơn", "shipment", saved));
        } catch (GoshipApiException e) {
            return upstream(e);
        } catch (Exception e) {
            log.error("Failed to book Goship shipment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không tạo được vận đơn"));
        }
    }

    @PatchMapping("/shipments/{id}/refresh")
    public ResponseEntity<?> refresh(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(Map.of("shipment", shipmentService.refreshStatus(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (GoshipApiException e) {
            return upstream(e);
        }
    }

    /** Goship refused or could not be reached - a 502, since nothing the caller sent is at fault. */
    private ResponseEntity<?> upstream(GoshipApiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
