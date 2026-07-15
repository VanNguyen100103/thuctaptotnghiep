package com.ut.edu.backend.auth;

import com.ut.edu.backend.user.User;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Verification Token for email verification and password reset
 */
@Entity
@Table(name = "verification_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenType tokenType;

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    public enum TokenType {
        EMAIL_VERIFICATION,
        PASSWORD_RESET,
        TWO_FACTOR_AUTH
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }
}
