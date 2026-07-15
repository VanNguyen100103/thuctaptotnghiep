package com.ut.edu.backend.auth;

import com.ut.edu.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findByUserAndOtpCodeAndOtpType(
            User user,
            String otpCode,
            OtpVerification.OtpType otpType
    );

    Optional<OtpVerification> findTopByUserAndOtpTypeOrderByCreatedAtDesc(
            User user,
            OtpVerification.OtpType otpType
    );

    @Modifying
    @Query("DELETE FROM OtpVerification o WHERE o.expiryDate < :now")
    void deleteExpiredOtps(LocalDateTime now);

    @Modifying
    @Query("DELETE FROM OtpVerification o WHERE o.user = :user AND o.otpType = :otpType")
    void deleteByUserAndOtpType(User user, OtpVerification.OtpType otpType);
}
