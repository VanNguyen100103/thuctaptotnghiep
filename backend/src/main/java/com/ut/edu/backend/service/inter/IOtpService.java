package com.ut.edu.backend.service.inter;

import com.ut.edu.backend.model.OtpVerification;
import com.ut.edu.backend.model.User;

public interface IOtpService {

    /**
     * Generate and save a new OTP for user
     */
    String generateOtp(User user, OtpVerification.OtpType otpType);

    /**
     * Verify OTP code
     */
    boolean verifyOtp(User user, String otpCode, OtpVerification.OtpType otpType);

    /**
     * Invalidate all OTPs for a user and type
     */
    void invalidateOtps(User user, OtpVerification.OtpType otpType);

    /**
     * Clean up expired OTPs
     */
    void cleanupExpiredOtps();
}
