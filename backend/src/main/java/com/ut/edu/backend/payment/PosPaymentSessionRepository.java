package com.ut.edu.backend.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PosPaymentSessionRepository extends JpaRepository<PosPaymentSession, Long> {

    /** Webhook path: the transfer content is the only handle an incoming payment gives us. */
    Optional<PosPaymentSession> findByReference(String reference);

    boolean existsByReference(String reference);
}
