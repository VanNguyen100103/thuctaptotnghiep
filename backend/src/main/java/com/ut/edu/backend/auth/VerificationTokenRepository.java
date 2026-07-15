package com.ut.edu.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for VerificationToken entity
 */
@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByToken(String token);

    Optional<VerificationToken> findByUserIdAndTokenType(Long userId, VerificationToken.TokenType tokenType);

    @Modifying
    @Query("DELETE FROM VerificationToken t WHERE t.expiryDate < ?1")
    void deleteExpiredTokens(LocalDateTime now);

    void deleteByUserId(Long userId);
}
