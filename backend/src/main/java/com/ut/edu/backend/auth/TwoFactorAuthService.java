package com.ut.edu.backend.auth;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.UserRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TwoFactorAuthService {

    private final UserRepository userRepository;
    private final TwoFactorAuthRepository twoFactorAuthRepository;
    private final GoogleAuthenticator googleAuthenticator;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int BACKUP_CODE_LENGTH = 8;
    private static final int BACKUP_CODES_COUNT = 10;

    @Value("${app.name:E-commerce Fashion Store}")
    private String appName;

    @Transactional
    public EnableTwoFactorResponse enableTwoFactor(User user) {
        log.info("Enabling 2FA for user: {}", user.getUsername());

        // Check if 2FA already exists
        TwoFactorAuth existing = twoFactorAuthRepository.findByUser(user).orElse(null);
        if (existing != null && existing.getEnabled()) {
            throw new IllegalStateException("2FA is already enabled for this user");
        }

        // Generate TOTP secret
        final GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        String secret = key.getKey();

        // Generate backup codes
        List<String> backupCodes = generateBackupCodes();

        // Create or update 2FA entity (not enabled yet until verified)
        TwoFactorAuth twoFactorAuth;
        if (existing != null) {
            existing.setSecret(secret);
            existing.setBackupCodes(backupCodes);
            existing.setEnabled(false);
            twoFactorAuth = twoFactorAuthRepository.save(existing);
        } else {
            twoFactorAuth = TwoFactorAuth.builder()
                    .user(user)
                    .secret(secret)
                    .backupCodes(backupCodes)
                    .enabled(false)
                    .build();
            twoFactorAuth = twoFactorAuthRepository.save(twoFactorAuth);
        }

        // Generate QR code
        String qrCodeDataUrl = generateQRCodeDataUrl(user.getUsername(), secret);

        log.info("2FA setup initiated for user: {} (not yet activated)", user.getUsername());

        return new EnableTwoFactorResponse(secret, qrCodeDataUrl, backupCodes);
    }

    @Transactional
    public boolean verifyAndActivateTwoFactor(User user, String totpCode) {
        log.info("Verifying and activating 2FA for user: {}", user.getUsername());

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("2FA setup not found. Please start setup first."));

        // Verify TOTP code
        boolean isValid = googleAuthenticator.authorize(twoFactorAuth.getSecret(), Integer.parseInt(totpCode));

        if (!isValid) {
            log.warn("Invalid TOTP code for 2FA activation: {}", user.getUsername());
            return false;
        }

        // Activate 2FA
        twoFactorAuth.setEnabled(true);
        twoFactorAuth.setEnabledAt(LocalDateTime.now());
        twoFactorAuthRepository.save(twoFactorAuth);
        user.setTwoFactorEnabled(true);
        user.setEnabled(true);
        userRepository.save(user);

        log.info("2FA activated successfully for user: {}", user.getUsername());
        return true;
    }

    @Transactional
    public void disableTwoFactor(User user) {
        log.info("Disabling 2FA for user: {}", user.getUsername());

        twoFactorAuthRepository.findByUser(user).ifPresent(twoFactorAuth -> {
        // ✅ FIX 1: Detach relationship TRƯỚC KHI delete
        user.setTwoFactorAuth(null);
        
        // ✅ FIX 2: Update user first
        user.setTwoFactorEnabled(false);
        userRepository.save(user);
        
        // ✅ FIX 3: Delete TwoFactorAuth SAU KHI save user
        twoFactorAuthRepository.delete(twoFactorAuth);
        
        log.info("2FA disabled for user: {}", user.getUsername());
    });
}

    public boolean verifyTotpCode(User user, String code) {
        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUser(user).orElse(null);

        if (twoFactorAuth == null || !twoFactorAuth.getEnabled()) {
            return false;
        }

        try {
            int totpCode = Integer.parseInt(code);
            boolean isValid = googleAuthenticator.authorize(twoFactorAuth.getSecret(), totpCode);

            if (isValid) {
                twoFactorAuth.markAsUsed();
                twoFactorAuthRepository.save(twoFactorAuth);
                log.info("TOTP code verified for user: {}", user.getUsername());
            }

            return isValid;
        } catch (NumberFormatException e) {
            log.warn("Invalid TOTP code format: {}", code);
            return false;
        }
    }

    @Transactional
    public boolean verifyBackupCode(User user, String code) {
        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUser(user).orElse(null);

        if (twoFactorAuth == null || !twoFactorAuth.getEnabled()) {
            return false;
        }

        if (twoFactorAuth.isBackupCodeValid(code)) {
            twoFactorAuth.useBackupCode(code);
            twoFactorAuthRepository.save(twoFactorAuth);
            log.info("Backup code used for user: {} ({} codes remaining)",
                    user.getUsername(), twoFactorAuth.getBackupCodes().size());
            return true;
        }

        return false;
    }

    @Transactional
    public List<String> regenerateBackupCodes(User user) {
        log.info("Regenerating backup codes for user: {}", user.getUsername());

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("2FA is not enabled"));

        List<String> newBackupCodes = generateBackupCodes();
        twoFactorAuth.setBackupCodes(newBackupCodes);
        twoFactorAuthRepository.save(twoFactorAuth);

        log.info("Backup codes regenerated for user: {}", user.getUsername());
        return newBackupCodes;
    }

    public boolean isTwoFactorEnabled(User user) {
        return twoFactorAuthRepository.findByUser(user)
                .map(TwoFactorAuth::getEnabled)
                .orElse(false);
    }

    /**
     * Generate 10 random backup codes (8 characters each)
     */
    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        for (int i = 0; i < BACKUP_CODES_COUNT; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                code.append(chars.charAt(secureRandom.nextInt(chars.length())));
            }
            codes.add(code.toString());
        }

        return codes;
    }

    /**
     * Generate QR code as Base64 data URL
     */
    private String generateQRCodeDataUrl(String username, String secret) {
        try {
            // Generate OTP Auth URL
            String otpAuthUrl = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(
                    appName,
                    username,
                    new GoogleAuthenticatorKey.Builder(secret).build()
            );

            // Generate QR code
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpAuthUrl, BarcodeFormat.QR_CODE, 300, 300);

            // Convert to PNG bytes
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();

            // Convert to Base64 data URL
            String base64Image = Base64.getEncoder().encodeToString(pngData);
            return "data:image/png;base64," + base64Image;

        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code", e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}
