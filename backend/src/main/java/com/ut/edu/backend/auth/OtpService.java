package com.ut.edu.backend.auth;

import com.ut.edu.backend.user.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OtpService {

    private final OtpVerificationRepository otpRepository;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 10;

    @Transactional
    public String generateOtp(User user, OtpVerification.OtpType otpType) {
        // Invalidate any existing OTPs for this user and type
        invalidateOtps(user, otpType);

        // Generate 6-digit OTP
        String otpCode = generateRandomOtp();

        // Create and save OTP verification
        OtpVerification otpVerification = OtpVerification.builder()
                .user(user)
                .otpCode(otpCode)
                .otpType(otpType)
                .expiryDate(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .isUsed(false)
                .attemptCount(0)
                .build();

        otpRepository.save(otpVerification);

        log.info("Generated OTP for user: {} (type: {})", user.getUsername(), otpType);
        return otpCode;
    }

    @Transactional
    public boolean verifyOtp(User user, String otpCode, OtpVerification.OtpType otpType) {
        Optional<OtpVerification> otpOptional = otpRepository
                .findByUserAndOtpCodeAndOtpType(user, otpCode, otpType);

        if (otpOptional.isEmpty()) {
            log.warn("OTP not found for user: {} (code: {})", user.getUsername(), otpCode);
            return false;
        }

        OtpVerification otp = otpOptional.get();

        // Increment attempt count
        otp.setAttemptCount(otp.getAttemptCount() + 1);
        otpRepository.save(otp);

        // Check if OTP is valid
        if (!otp.isValid()) {
            if (otp.isExpired()) {
                log.warn("OTP expired for user: {}", user.getUsername());
            } else if (otp.getIsUsed()) {
                log.warn("OTP already used for user: {}", user.getUsername());
            } else if (otp.getAttemptCount() >= 5) {
                log.warn("OTP max attempts exceeded for user: {}", user.getUsername());
            }
            return false;
        }

        // Mark OTP as used
        otp.setIsUsed(true);
        otpRepository.save(otp);

        log.info("OTP verified successfully for user: {}", user.getUsername());
        return true;
    }

    @Transactional
    public void invalidateOtps(User user, OtpVerification.OtpType otpType) {
        otpRepository.deleteByUserAndOtpType(user, otpType);
        log.debug("Invalidated all OTPs for user: {} (type: {})", user.getUsername(), otpType);
    }

    @Transactional
    public void cleanupExpiredOtps() {
        otpRepository.deleteExpiredOtps(LocalDateTime.now());
        log.info("Cleaned up expired OTPs");
    }

    /**
     * Generate a random 6-digit OTP
     */
    private String generateRandomOtp() {
        int otp = secureRandom.nextInt(900000) + 100000; // Range: 100000-999999
        return String.valueOf(otp);
    }
}
