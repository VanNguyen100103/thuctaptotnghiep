package com.ut.edu.backend.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findFirstByStoreIdAndStatusOrderByStartDateDesc(Long storeId, SubscriptionStatus status);

    List<Subscription> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    Optional<Subscription> findByPaypalSubscriptionId(String paypalSubscriptionId);

    /**
     * Subscriptions that have passed their end date but are still marked ACTIVE -
     * picked up by the daily expiry job.
     */
    List<Subscription> findByStatusAndEndDateBefore(SubscriptionStatus status, LocalDate date);
}
