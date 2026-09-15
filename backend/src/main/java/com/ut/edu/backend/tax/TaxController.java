package com.ut.edu.backend.tax;

import com.ut.edu.backend.exception.ResourceNotFoundException;
import com.ut.edu.backend.exception.SubscriptionRequiredException;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * "Thuế & Kế toán" - the module behind the tab of the same name, serving the
 * 01/CNKD declaration screens and their Thiết lập.
 *
 * Read endpoints do not require an active subscription, only writes do: a
 * shop whose plan has lapsed must still be able to look up what it owes the
 * tax office, which is exactly the moment it is least able to pay for
 * anything. Same OWNER/MANAGER gate as the rest of /store - a STAFF account
 * at the register has no business seeing the household's MST.
 */
@RestController
@RequestMapping("/store/tax")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class TaxController {

    private final TaxDeclarationService declarationService;
    private final TenantGuard tenantGuard;
    private final SubscriptionGuard subscriptionGuard;
    private final AuthorizationService authorizationService;

    /** Overridable only in tests; every screen reads "hôm nay" through here so period state stays consistent within a request. */
    protected LocalDate today() {
        return LocalDate.now();
    }

    // ------------------------------------------------------------------
    // Thiết lập
    // ------------------------------------------------------------------

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        Long storeId = tenantGuard.requireStore();
        return ResponseEntity.ok(TaxProfileResponse.of(declarationService.getOrCreateProfile(storeId)));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> saveProfile(@Valid @RequestBody TaxProfileRequest request) {
        try {
            Long storeId = tenantGuard.requireStore();
            subscriptionGuard.requireActiveSubscription(storeId);
            TaxProfile saved = declarationService.saveProfile(storeId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Đã lưu thiết lập thuế",
                    "profile", TaxProfileResponse.of(saved)));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to save tax profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không lưu được thiết lập thuế"));
        }
    }

    /**
     * The options the Thiết lập pickers offer, rates included, so the screen
     * never hard-codes a percentage the law owns.
     */
    @GetMapping("/activities")
    public ResponseEntity<?> activities() {
        List<Map<String, Object>> activities = Arrays.stream(TaxActivity.values())
                .map(a -> Map.<String, Object>of(
                        "value", a.name(),
                        "label", a.getLabel(),
                        "vatRate", a.getVatRate(),
                        "pitRate", a.getPitRate()))
                .toList();
        return ResponseEntity.ok(Map.of("activities", activities));
    }

    // ------------------------------------------------------------------
    // Tờ khai 01/CNKD
    // ------------------------------------------------------------------

    /** GET /api/store/tax/declarations?year=2026 - the whole 01/CNKD screen in one call. */
    @GetMapping("/declarations")
    public ResponseEntity<?> declarations(@RequestParam(required = false) Integer year) {
        try {
            Long storeId = tenantGuard.requireStore();
            LocalDate today = today();
            return ResponseEntity.ok(declarationService.yearSummary(
                    storeId, year == null ? today.getYear() : year, today));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to build tax declarations for year {}", year, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không tải được danh sách tờ khai"));
        }
    }

    /** GET /api/store/tax/declarations/2026/1 - "Xem chi tiết" on one period. */
    @GetMapping("/declarations/{year}/{periodNumber}")
    public ResponseEntity<?> declarationDetail(@PathVariable int year, @PathVariable int periodNumber) {
        try {
            Long storeId = tenantGuard.requireStore();
            return ResponseEntity.ok(declarationService.detail(storeId, year, periodNumber, today()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to build tax declaration detail {}/{}", year, periodNumber, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không tải được chi tiết tờ khai"));
        }
    }

    /** Body of the status dropdown on the 01/CNKD list: "Đã nộp" / "Chưa nộp". */
    public record SubmitRequest(boolean submitted) {
    }

    @PatchMapping("/declarations/{year}/{periodNumber}/status")
    public ResponseEntity<?> setStatus(@PathVariable int year,
                                       @PathVariable int periodNumber,
                                       @RequestBody SubmitRequest request) {
        try {
            Long storeId = tenantGuard.requireStore();
            subscriptionGuard.requireActiveSubscription(storeId);
            User currentUser = authorizationService.getCurrentUser();
            TaxDeclarationRowResponse row = declarationService.setSubmitted(
                    storeId, year, periodNumber, request.submitted(), currentUser, today());
            return ResponseEntity.ok(Map.of(
                    "message", request.submitted() ? "Đã đánh dấu tờ khai đã nộp" : "Đã bỏ đánh dấu đã nộp",
                    "declaration", row));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to set tax declaration status {}/{}", year, periodNumber, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không cập nhật được trạng thái tờ khai"));
        }
    }
}
