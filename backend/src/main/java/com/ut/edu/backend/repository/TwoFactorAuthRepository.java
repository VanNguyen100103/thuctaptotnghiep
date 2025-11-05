package com.ut.edu.backend.repository;

import com.ut.edu.backend.model.TwoFactorAuth;
import com.ut.edu.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TwoFactorAuthRepository extends JpaRepository<TwoFactorAuth, Long> {

    Optional<TwoFactorAuth> findByUser(User user);

    Optional<TwoFactorAuth> findByUserId(Long userId);

    boolean existsByUser(User user);

    void deleteByUser(User user);
}
