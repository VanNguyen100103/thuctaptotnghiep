package com.ut.edu.backend.store;

import com.ut.edu.backend.exception.SubscriptionRequiredException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Enforces subscription state before a store mutates data: no active
 * subscription (missing/expired) means read-only, and BASIC-tier stores are
 * capped on products/staff. Mirrors {@link TenantGuard}'s style - callers
 * invoke this explicitly at the point of mutation.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionGuard {

    private final SubscriptionRepository subscriptionRepository;

    private Subscription requireSubscription(Long storeId) {
        return subscriptionRepository
                .findFirstByStoreIdAndStatusOrderByStartDateDesc(storeId, SubscriptionStatus.ACTIVE)
                .filter(Subscription::isCurrentlyActive)
                .orElseThrow(() -> new SubscriptionRequiredException(
                        "Your store does not have an active subscription. Renew or upgrade to continue."));
    }

    /** Blocks mutations on stores with no active (or expired) subscription. */
    public void requireActiveSubscription(Long storeId) {
        requireSubscription(storeId);
    }

    public void requireCanAddProduct(Long storeId, long currentProductCount) {
        Subscription subscription = requireSubscription(storeId);
        int max = subscription.getPlan().getMaxProducts();
        if (max >= 0 && currentProductCount >= max) {
            throw new SubscriptionRequiredException(
                    "Your %s plan allows up to %d products. Upgrade to add more."
                            .formatted(subscription.getPlan(), max));
        }
    }

    public void requireCanAddStaff(Long storeId, long currentStaffCount) {
        Subscription subscription = requireSubscription(storeId);
        int max = subscription.getPlan().getMaxStaff();
        if (max >= 0 && currentStaffCount >= max) {
            throw new SubscriptionRequiredException(
                    "Your %s plan allows up to %d staff member(s). Upgrade to add more."
                            .formatted(subscription.getPlan(), max));
        }
    }
}
