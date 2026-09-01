package com.ut.edu.backend.store;

import com.ut.edu.backend.email.EmailService;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily sweep that expires subscriptions past their end date and notifies
 * the store OWNER. {@link #run()} is the (untestable) scheduled trigger;
 * {@link #processExpiredSubscriptions(LocalDate)} is the plain, unit-testable
 * core logic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryJob {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 1 * * *") // 01:00 daily
    public void run() {
        processExpiredSubscriptions(LocalDate.now());
    }

    @Transactional
    public void processExpiredSubscriptions(LocalDate asOf) {
        List<Subscription> overdue =
                subscriptionRepository.findByStatusAndEndDateBefore(SubscriptionStatus.ACTIVE, asOf);

        for (Subscription subscription : overdue) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);

            Store store = subscription.getStore();
            log.info("Subscription expired: store={}, plan={}, endDate={}",
                    store.getSlug(), subscription.getPlan(), subscription.getEndDate());

            userRepository.findByStoreIdAndStoreRole(store.getId(), StoreRole.OWNER)
                    .ifPresentOrElse(
                            owner -> trySendExpiryEmail(owner, store, subscription),
                            () -> log.warn("No OWNER user found for expired store {}", store.getSlug()));
        }
        log.info("Subscription expiry job processed {} subscription(s)", overdue.size());
    }

    private void trySendExpiryEmail(User owner, Store store, Subscription subscription) {
        try {
            emailService.sendSubscriptionExpiredEmail(
                    owner.getEmail(), store.getName(), subscription.getPlan().name());
        } catch (Exception e) {
            log.error("Failed to send subscription-expired email for store {}", store.getSlug(), e);
        }
    }
}
