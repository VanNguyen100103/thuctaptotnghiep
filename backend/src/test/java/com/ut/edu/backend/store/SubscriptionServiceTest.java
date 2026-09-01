package com.ut.edu.backend.store;

import com.ut.edu.backend.payment.PayPalApiException;
import com.ut.edu.backend.payment.PayPalRestClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final String BASIC_PLAN_ID = "P-TEST-BASIC";
    private static final String PRO_PLAN_ID = "P-TEST-PRO";

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private PayPalRestClient payPalRestClient;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Store store;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(subscriptionService, "basicPlanId", BASIC_PLAN_ID);
        ReflectionTestUtils.setField(subscriptionService, "proPlanId", PRO_PLAN_ID);
        ReflectionTestUtils.setField(subscriptionService, "frontendUrl", "https://example.com");
        store = Store.builder().id(10L).name("Shop A").slug("shop-a").status(StoreStatus.ACTIVE).build();
    }

    // ---------- createSubscription ----------

    @Test
    void createSubscription_happyPath_returnsApproveLink() {
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(payPalRestClient.post(eq("/v1/billing/subscriptions"), any())).thenReturn(Map.of(
                "id", "I-NEW123",
                "links", List.of(
                        Map.of("rel", "self", "href", "https://api.sandbox.paypal.com/x"),
                        Map.of("rel", "approve", "href", "https://www.sandbox.paypal.com/webapps/billing/subscriptions?ba_token=abc"))));

        String approveUrl = subscriptionService.createSubscription(10L, SubscriptionPlan.BASIC);

        assertThat(approveUrl).isEqualTo("https://www.sandbox.paypal.com/webapps/billing/subscriptions?ba_token=abc");
    }

    @Test
    void createSubscription_noApproveLinkInResponse_throws() {
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(payPalRestClient.post(eq("/v1/billing/subscriptions"), any()))
                .thenReturn(Map.of("id", "I-NEW123", "links", List.of()));

        assertThatThrownBy(() -> subscriptionService.createSubscription(10L, SubscriptionPlan.BASIC))
                .isInstanceOf(PayPalApiException.class);
    }

    @Test
    void createSubscription_alreadyHasPaidActiveSubscription_rejectedWithoutCallingPayPal() {
        Subscription existing = Subscription.builder()
                .plan(SubscriptionPlan.BASIC).status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now()).paypalSubscriptionId("I-EXISTING").build();
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> subscriptionService.createSubscription(10L, SubscriptionPlan.PRO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already have an active paid subscription");

        verifyNoInteractions(payPalRestClient);
    }

    @Test
    void createSubscription_freeTrialCurrentlyActive_stillAllowedToSubscribe() {
        Subscription trial = Subscription.builder()
                .plan(SubscriptionPlan.FREE_TRIAL).status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now()).paypalSubscriptionId(null).build();
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(trial));
        when(payPalRestClient.post(eq("/v1/billing/subscriptions"), any())).thenReturn(Map.of(
                "id", "I-NEW123",
                "links", List.of(Map.of("rel", "approve", "href", "https://approve.example"))));

        assertThatCode(() -> subscriptionService.createSubscription(10L, SubscriptionPlan.BASIC))
                .doesNotThrowAnyException();
    }

    // ---------- cancelSubscription ----------

    @Test
    void cancelSubscription_happyPath_callsPayPalCancel() {
        Subscription paid = Subscription.builder()
                .plan(SubscriptionPlan.PRO).status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now()).paypalSubscriptionId("I-ABC").build();
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(paid));

        subscriptionService.cancelSubscription(10L);

        verify(payPalRestClient).postNoContent(eq("/v1/billing/subscriptions/I-ABC/cancel"), any());
    }

    @Test
    void cancelSubscription_freeTrial_rejected() {
        Subscription trial = Subscription.builder()
                .plan(SubscriptionPlan.FREE_TRIAL).status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now()).paypalSubscriptionId(null).build();
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(trial));

        assertThatThrownBy(() -> subscriptionService.cancelSubscription(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("free trial");

        verifyNoInteractions(payPalRestClient);
    }

    @Test
    void cancelSubscription_noActiveSubscription_rejected() {
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.cancelSubscription(10L))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- handleActivated ----------

    private Map<String, Object> activatedResource(String id, String customId, String planId) {
        Map<String, Object> resource = new java.util.HashMap<>();
        resource.put("id", id);
        resource.put("custom_id", customId);
        resource.put("plan_id", planId);
        return resource;
    }

    @Test
    void handleActivated_createsRowAndSupersedesOldOne() {
        when(subscriptionRepository.findByPaypalSubscriptionId("I-NEW")).thenReturn(Optional.empty());
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        Subscription trial = Subscription.builder()
                .plan(SubscriptionPlan.FREE_TRIAL).status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now()).build();
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(trial));

        subscriptionService.handleActivated(activatedResource("I-NEW", "10", BASIC_PLAN_ID));

        assertThat(trial.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(2)).save(captor.capture());
        Subscription inserted = captor.getAllValues().get(1);
        assertThat(inserted.getPlan()).isEqualTo(SubscriptionPlan.BASIC);
        assertThat(inserted.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(inserted.getPaypalSubscriptionId()).isEqualTo("I-NEW");
        assertThat(inserted.getEndDate()).isNull();
    }

    @Test
    void handleActivated_redelivery_isIdempotentNoOp() {
        when(subscriptionRepository.findByPaypalSubscriptionId("I-NEW"))
                .thenReturn(Optional.of(Subscription.builder().paypalSubscriptionId("I-NEW").build()));

        subscriptionService.handleActivated(activatedResource("I-NEW", "10", BASIC_PLAN_ID));

        verifyNoInteractions(storeRepository);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void handleActivated_missingCustomId_fallsBackToGet() {
        when(subscriptionRepository.findByPaypalSubscriptionId("I-NEW")).thenReturn(Optional.empty());
        when(payPalRestClient.get("/v1/billing/subscriptions/I-NEW")).thenReturn(Map.of("custom_id", "10"));
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        when(subscriptionRepository.findFirstByStoreIdAndStatusOrderByStartDateDesc(10L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        subscriptionService.handleActivated(activatedResource("I-NEW", null, BASIC_PLAN_ID));

        verify(payPalRestClient).get("/v1/billing/subscriptions/I-NEW");
        verify(subscriptionRepository).save(any());
    }

    @Test
    void handleActivated_unrecognizedPlanId_throwsInsteadOfDefaulting() {
        when(subscriptionRepository.findByPaypalSubscriptionId("I-NEW")).thenReturn(Optional.empty());
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));

        assertThatThrownBy(() -> subscriptionService.handleActivated(activatedResource("I-NEW", "10", "P-UNKNOWN")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unrecognized PayPal plan_id");
    }

    // ---------- handleCancelled / handleExpired ----------

    @Test
    void handleCancelled_flipsStatus() {
        Subscription active = Subscription.builder()
                .paypalSubscriptionId("I-X").status(SubscriptionStatus.ACTIVE).build();
        when(subscriptionRepository.findByPaypalSubscriptionId("I-X")).thenReturn(Optional.of(active));

        subscriptionService.handleCancelled(Map.of("id", "I-X"));

        assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(subscriptionRepository).save(active);
    }

    @Test
    void handleCancelled_alreadyCancelled_idempotentNoOp() {
        Subscription cancelled = Subscription.builder()
                .paypalSubscriptionId("I-X").status(SubscriptionStatus.CANCELLED).build();
        when(subscriptionRepository.findByPaypalSubscriptionId("I-X")).thenReturn(Optional.of(cancelled));

        subscriptionService.handleCancelled(Map.of("id", "I-X"));

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void handleExpired_flipsStatus() {
        Subscription active = Subscription.builder()
                .paypalSubscriptionId("I-Y").status(SubscriptionStatus.ACTIVE).build();
        when(subscriptionRepository.findByPaypalSubscriptionId("I-Y")).thenReturn(Optional.of(active));

        subscriptionService.handleExpired(Map.of("id", "I-Y"));

        assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void handleCancelled_unknownSubscriptionId_logsAndDoesNotThrow() {
        when(subscriptionRepository.findByPaypalSubscriptionId(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> subscriptionService.handleCancelled(Map.of("id", "I-UNKNOWN")))
                .doesNotThrowAnyException();
    }

    // ---------- handleRecurringPaymentSale ----------

    @Test
    void handleRecurringPaymentSale_doesNotThrowOrTouchRepositories() {
        assertThatCode(() -> subscriptionService.handleRecurringPaymentSale(
                Map.of("billing_agreement_id", "I-X", "amount", Map.of("total", "5.00"))))
                .doesNotThrowAnyException();

        verifyNoInteractions(subscriptionRepository, storeRepository, payPalRestClient);
    }
}
