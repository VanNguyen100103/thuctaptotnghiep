package com.ut.edu.backend.auth;

import java.util.List;

/**
 * Response DTO for enabling 2FA
 */
public record EnableTwoFactorResponse(
    String secret,
    String qrCodeDataUrl,
    List<String> backupCodes
) {}
