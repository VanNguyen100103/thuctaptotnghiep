package com.ut.edu.backend.service.inter;

import com.ut.edu.backend.model.User;

import java.util.List;

public interface ITwoFactorAuthService {

    /**
     * Enable 2FA for user - generates secret and backup codes
     * Returns QR code data URL and backup codes
     */
    EnableTwoFactorResponse enableTwoFactor(User user);

    /**
     * Verify TOTP code and complete 2FA setup
     */
    boolean verifyAndActivateTwoFactor(User user, String totpCode);

    /**
     * Disable 2FA for user
     */
    void disableTwoFactor(User user);

    /**
     * Verify TOTP code during login
     */
    boolean verifyTotpCode(User user, String code);

    /**
     * Verify backup code during login
     */
    boolean verifyBackupCode(User user, String code);

    /**
     * Generate new backup codes (invalidates old ones)
     */
    List<String> regenerateBackupCodes(User user);

    /**
     * Check if user has 2FA enabled
     */
    boolean isTwoFactorEnabled(User user);

    /**
     * Response DTO for enabling 2FA
     */
    record EnableTwoFactorResponse(
        String secret,
        String qrCodeDataUrl,
        List<String> backupCodes
    ) {}
}
