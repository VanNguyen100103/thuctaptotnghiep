package com.ut.edu.backend.auth;

import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.user.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Zalo Login v4.
 *
 * Two flows share one redirect. The browser is sent to Zalo the same way for
 * both, and it is the page that started it which decides what the returned
 * code means: /callback to sign in, /link to attach a Zalo account to whoever
 * is already signed in.
 *
 * Signing in with Zalo only works for an account that has been linked first.
 * Zalo gives no email and no phone - just an id scoped to this application -
 * so there is nothing to recognise a stranger by. See V31.
 */
@RestController
@RequestMapping("/auth/zalo")
@RequiredArgsConstructor
@Slf4j
public class ZaloAuthController {

    private final ZaloAuthService zaloAuthService;
    private final UserService userService;
    private final AuthorizationService authorizationService;

    public record ZaloCallbackRequest(
            @NotBlank(message = "Thiếu mã đăng nhập Zalo") String code,
            @NotBlank(message = "Thiếu state") String state) {
    }

    /**
     * Where to send the browser. Public, because signing in obviously cannot
     * require being signed in - and linking starts from the same place.
     */
    @GetMapping("/authorize-url")
    public ResponseEntity<?> authorizeUrl() {
        try {
            return ResponseEntity.ok(Map.of("url", zaloAuthService.buildAuthorizeUrl()));
        } catch (ZaloAuthException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Sign in with a Zalo account that has already been linked.
     *
     * An unlinked account is a 404 with an explanation rather than an error:
     * it is the expected answer the first time somebody tries, and the fix is
     * something they do, not something that went wrong.
     */
    @PostMapping("/callback")
    public ResponseEntity<?> callback(@Valid @RequestBody ZaloCallbackRequest request) {
        try {
            String zaloUserId = zaloAuthService.exchangeCodeForZaloUserId(request.code(), request.state());
            return ResponseEntity.ok(userService.authenticateByZaloUserId(zaloUserId));
        } catch (ZaloAuthException e) {
            log.warn("Zalo sign-in failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /** Attach the Zalo account that just approved to the signed-in user. */
    @PostMapping("/link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> link(@Valid @RequestBody ZaloCallbackRequest request) {
        try {
            String zaloUserId = zaloAuthService.exchangeCodeForZaloUserId(request.code(), request.state());
            userService.linkZaloAccount(authorizationService.getCurrentUser().getId(), zaloUserId);
            return ResponseEntity.ok(Map.of("message", "Đã liên kết tài khoản Zalo", "linked", true));
        } catch (ZaloAuthException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            // Somebody else already linked this Zalo account.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> unlink() {
        userService.unlinkZaloAccount(authorizationService.getCurrentUser().getId());
        return ResponseEntity.ok(Map.of("message", "Đã hủy liên kết Zalo", "linked", false));
    }

    /** Whether the signed-in user has a Zalo account attached - what the account page renders from. */
    @GetMapping("/link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> linkStatus() {
        boolean linked = authorizationService.getCurrentUser().getZaloUserId() != null;
        return ResponseEntity.ok(Map.of("linked", linked, "available", zaloAuthService.isConfigured()));
    }
}
