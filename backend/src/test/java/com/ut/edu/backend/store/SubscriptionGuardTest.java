package com.ut.edu.backend.store;

import com.ut.edu.backend.exception.SubscriptionRequiredException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionGuardTest {

    @Mock private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionGuard subscriptionGuard;

    private Subscription activeSubscription(SubscriptionPlan plan) {
        return Subscription.builder()
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(10))
                .build();
    }

    @Test
    void requireActiveSubscription_activeNonExpired_passes() {
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription(SubscriptionPlan.PRO)));

        assertThatCode(() -> subscriptionGuard.requireActiveSubscription(10L)).doesNotThrowAnyException();
    }

    @Test
    void requireActiveSubscription_noActiveRow_throws() {
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionGuard.requireActiveSubscription(10L))
                .isInstanceOf(SubscriptionRequiredException.class)
                .hasMessageContaining("does not have an active subscription");
    }

    @Test
    void requireActiveSubscription_pastEndDateJobNotRunYet_throws() {
        Subscription lapsed = Subscription.builder()
                .plan(SubscriptionPlan.BASIC)
                .status(SubscriptionStatus.ACTIVE) // job hasn't flipped it to EXPIRED yet
                .startDate(LocalDate.now().minusDays(30))
                .endDate(LocalDate.now().minusDays(1))
                .build();
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(lapsed));

        assertThatThrownBy(() -> subscriptionGuard.requireActiveSubscription(10L))
                .isInstanceOf(SubscriptionRequiredException.class);
    }

    @Test
    void requireCanAddProduct_underLimit_passes() {
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription(SubscriptionPlan.BASIC)));

        assertThatCode(() -> subscriptionGuard.requireCanAddProduct(10L, 49)).doesNotThrowAnyException();
    }

    @Test
    void requireCanAddProduct_atLimit_throws() {
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription(SubscriptionPlan.BASIC)));

        assertThatThrownBy(() -> subscriptionGuard.requireCanAddProduct(10L, 50))
                .isInstanceOf(SubscriptionRequiredException.class)
                .hasMessageContaining("50 products");
    }

    @Test
    void requireCanAddProduct_unlimitedPlan_alwaysPasses() {
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription(SubscriptionPlan.PRO)));

        assertThatCode(() -> subscriptionGuard.requireCanAddProduct(10L, 100_000)).doesNotThrowAnyException();
    }

    @Test
    void requireCanAddStaff_underLimit_passes() {
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription(SubscriptionPlan.BASIC)));

        assertThatCode(() -> subscriptionGuard.requireCanAddStaff(10L, 0)).doesNotThrowAnyException();
    }

    @Test
    void requireCanAddStaff_atLimit_throws() {
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSubscription(SubscriptionPlan.BASIC)));

        assertThatThrownBy(() -> subscriptionGuard.requireCanAddStaff(10L, 1))
                .isInstanceOf(SubscriptionRequiredException.class)
                .hasMessageContaining("1 staff");
    }
}
