package com.ut.edu.backend.payment;

import com.ut.edu.backend.exception.SubscriptionRequiredException;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The counter half of SePay: "Chuyển khoản" on the Bán hàng screen.
 *
 * Split out from {@link PaymentController} (storefront gateway flows -
 * PayPal/MoMo/COD redirects and captures) because nothing here is a gateway
 * call: it hands out a transfer reference and reports whether money carrying
 * it has landed. The webhook that does the landing still lives next to the
 * order one in PaymentController, since SePay posts both to a single URL.
 */
@RestController
@RequestMapping("/payments/pos")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class PosPaymentController {

    private final PosPaymentSessionService sessionService;
    private final TenantGuard tenantGuard;
    private final SubscriptionGuard subscriptionGuard;
    private final AuthorizationService authorizationService;

    public record CreatePosQrRequest(
            @NotNull(message = "Số tiền là bắt buộc")
            @Positive(message = "Số tiền phải lớn hơn 0")
            BigDecimal amount) {
    }

    /**
     * POST /api/payments/pos/qr - the cashier is about to show a QR.
     *
     * Subscription is checked here rather than only at checkout: the sale
     * would be rejected at "Thanh toán" anyway, and finding that out after
     * the customer has already transferred is the one order to avoid.
     */
    @PostMapping("/qr")
    public ResponseEntity<?> createQr(@Valid @RequestBody CreatePosQrRequest request) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            User cashier = authorizationService.getCurrentUser();
            PosPaymentSession session = sessionService.create(cashier, request.amount());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(PosQrSessionResponse.from(session, sessionService.qrUrl(session)));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (SePayApiException e) {
            // Missing SEPAY_ACCOUNT_NUMBER etc - a deployment problem, and one
            // the cashier can act on ("dùng tiền mặt"), so say so plainly.
            log.error("Cannot create POS QR", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Chưa cấu hình tài khoản nhận chuyển khoản"));
        } catch (Exception e) {
            log.error("Failed to create POS payment session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không tạo được mã QR"));
        }
    }

    /**
     * GET /api/payments/pos/qr/{id} - polled by the terminal every few
     * seconds while the QR is on screen, until it reads PAID or expires.
     */
    @GetMapping("/qr/{id}")
    public ResponseEntity<?> getQr(@PathVariable Long id) {
        PosPaymentSession session = sessionService.getForCurrentStore(id);
        return ResponseEntity.ok(PosQrSessionResponse.from(session, sessionService.qrUrl(session)));
    }
}
