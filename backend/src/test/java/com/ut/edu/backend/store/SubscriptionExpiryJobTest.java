package com.ut.edu.backend.store;

import com.ut.edu.backend.email.EmailService;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryJobTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private SubscriptionExpiryJob job;

    private Store store;
    private Subscription lapsedSubscription;
    private final LocalDate today = LocalDate.of(2026, 9, 1);

    private void givenLapsedSubscription() {
        store = Store.builder().id(10L).name("Shop A").slug("shop-a").status(StoreStatus.ACTIVE).build();
        lapsedSubscription = Subscription.builder()
                .id(1L).store(store).plan(SubscriptionPlan.BASIC).status(SubscriptionStatus.ACTIVE)
                .startDate(today.minusDays(30)).endDate(today.minusDays(1))
                .build();
        when(subscriptionRepository.findByStatusAndEndDateBefore(SubscriptionStatus.ACTIVE, today))
                .thenReturn(List.of(lapsedSubscription));
    }

    @Test
    void processExpiredSubscriptions_flipsStatusAndSendsEmail() {
        givenLapsedSubscription();
        User owner = User.builder().id(1L).email("owner@shopa.vn").store(store).storeRole(StoreRole.OWNER).build();
        when(userRepository.findByStoreIdAndStoreRole(10L, StoreRole.OWNER)).thenReturn(Optional.of(owner));

        job.processExpiredSubscriptions(today);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);

        verify(emailService).sendSubscriptionExpiredEmail("owner@shopa.vn", "Shop A", "BASIC");
    }

    @Test
    void processExpiredSubscriptions_noOverdueRows_noOp() {
        when(subscriptionRepository.findByStatusAndEndDateBefore(SubscriptionStatus.ACTIVE, today))
                .thenReturn(List.of());

        job.processExpiredSubscriptions(today);

        verify(subscriptionRepository, never()).save(any());
        verify(emailService, never()).sendSubscriptionExpiredEmail(anyString(), anyString(), anyString());
    }

    @Test
    void processExpiredSubscriptions_emailThrows_statusChangeStillPersisted() {
        givenLapsedSubscription();
        User owner = User.builder().id(1L).email("owner@shopa.vn").store(store).storeRole(StoreRole.OWNER).build();
        when(userRepository.findByStoreIdAndStoreRole(10L, StoreRole.OWNER)).thenReturn(Optional.of(owner));
        doThrow(new RuntimeException("mailbox down"))
                .when(emailService).sendSubscriptionExpiredEmail(anyString(), anyString(), anyString());

        job.processExpiredSubscriptions(today);

        verify(subscriptionRepository).save(lapsedSubscription);
        assertThat(lapsedSubscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void processExpiredSubscriptions_noOwnerUser_logsAndSkipsEmailWithoutThrowing() {
        givenLapsedSubscription();
        when(userRepository.findByStoreIdAndStoreRole(10L, StoreRole.OWNER)).thenReturn(Optional.empty());

        job.processExpiredSubscriptions(today);

        verify(subscriptionRepository).save(lapsedSubscription);
        verify(emailService, never()).sendSubscriptionExpiredEmail(anyString(), anyString(), anyString());
    }
}
