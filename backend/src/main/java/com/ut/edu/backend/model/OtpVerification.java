package com.ut.edu.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 6)
    private String otpCode;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isUsed = false;

    @Builder.Default
    @Column(nullable = false)
    private Integer attemptCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OtpType otpType;

    public enum OtpType {
        REGISTRATION,
        PASSWORD_RESET,
        TWO_FACTOR_AUTH
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (expiryDate == null) {
            // OTP expires after 10 minutes
            expiryDate = LocalDateTime.now().plusMinutes(10);
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }

    public boolean isValid() {
        return !isUsed && !isExpired() && attemptCount < 5;
    }
}
