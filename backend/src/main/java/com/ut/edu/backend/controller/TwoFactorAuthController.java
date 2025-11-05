package com.ut.edu.backend.controller;

import com.ut.edu.backend.dto.TwoFactorSetupResponse;
import com.ut.edu.backend.dto.VerifyTotpRequest;
import com.ut.edu.backend.model.User;
import com.ut.edu.backend.repository.UserRepository;
import com.ut.edu.backend.security.UserPrincipal;
import com.ut.edu.backend.service.inter.ITwoFactorAuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/2fa")
@RequiredArgsConstructor
@Slf4j
public class TwoFactorAuthController {

    private final ITwoFactorAuthService twoFactorAuthService;
    private final UserRepository userRepository;

    /**
     * Enable 2FA - Step 1: Generate QR code and backup codes
     * POST /api/2fa/enable
     */
    @PostMapping("/enable")
    public ResponseEntity<?> enableTwoFactor(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("2FA enable request for user: {}", userPrincipal.getUsername());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            ITwoFactorAuthService.EnableTwoFactorResponse response =
                    twoFactorAuthService.enableTwoFactor(user);

            return ResponseEntity.ok(TwoFactorSetupResponse.from(
                    response.secret(),
                    response.qrCodeDataUrl(),
                    response.backupCodes()
            ));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Enable 2FA - Step 2: Verify TOTP code and activate
     * POST /api/2fa/verify
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyAndActivate(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody VerifyTotpRequest request) {

        log.info("2FA verification request for user: {}", userPrincipal.getUsername());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            boolean isValid = twoFactorAuthService.verifyAndActivateTwoFactor(user, request.getCode());

            if (isValid) {
                return ResponseEntity.ok(Map.of(
                        "message", "2FA enabled successfully",
                        "enabled", true
                ));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid TOTP code. Please try again."));
            }

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Disable 2FA
     * POST /api/2fa/disable
     */
    @PostMapping("/disable")
    public ResponseEntity<?> disableTwoFactor(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody VerifyTotpRequest request) {

        log.info("2FA disable request for user: {}", userPrincipal.getUsername());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Verify TOTP before disabling for security
            boolean isValid = twoFactorAuthService.verifyTotpCode(user, request.getCode());

            if (!isValid) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid TOTP code. Cannot disable 2FA."));
            }

            twoFactorAuthService.disableTwoFactor(user);
                
                log.info("2FA disabled successfully for user: {}", user.getUsername());

            return ResponseEntity.ok(Map.of(
                    "message", "2FA disabled successfully",
                    "enabled", false
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Regenerate backup codes
     * POST /api/2fa/backup-codes/regenerate
     */
    @PostMapping("/backup-codes/regenerate")
    public ResponseEntity<?> regenerateBackupCodes(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody VerifyTotpRequest request) {

        log.info("Backup codes regeneration request for user: {}", userPrincipal.getUsername());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Verify TOTP before regenerating for security
            boolean isValid = twoFactorAuthService.verifyTotpCode(user, request.getCode());

            if (!isValid) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid TOTP code. Cannot regenerate backup codes."));
            }

            List<String> newBackupCodes = twoFactorAuthService.regenerateBackupCodes(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Backup codes regenerated successfully. Save these codes in a safe place.",
                    "backupCodes", newBackupCodes
            ));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check 2FA status
     * GET /api/2fa/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getTwoFactorStatus(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("2FA status check for user: {}", userPrincipal.getUsername());

        try {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            boolean isEnabled = twoFactorAuthService.isTwoFactorEnabled(user);

            return ResponseEntity.ok(Map.of(
                    "enabled", isEnabled,
                    "username", user.getUsername()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
