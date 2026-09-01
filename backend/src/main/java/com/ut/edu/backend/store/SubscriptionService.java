package com.ut.edu.backend.store;

import com.ut.edu.backend.payment.PayPalApiException;
import com.ut.edu.backend.payment.PayPalRestClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owns what a PayPal subscription means for our domain: turning an owner's
 * "subscribe" request into a PayPal subscription, and turning PayPal's
 * webhook events back into {@link Subscription} rows. {@code payment/}
 * (PayPalRestClient) owns talking to PayPal; this owns the local state
 * transitions and the one-active-row invariant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final StoreRepository storeRepository;
    private final PayPalRestClient payPalRestClient;

    @Value("${paypal.plan.basic.id}")
    private String basicPlanId;

    @Value("${paypal.plan.pro.id}")
    private String proPlanId;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /** Creates a PayPal subscription and returns the buyer approval link. */
    @Transactional(readOnly = true)
    public String createSubscription(Long storeId, SubscriptionPlan plan) {
        Optional<Subscription> current = subscriptionRepository
                .findFirstByStoreIdAndStatusOrderByStartDateDesc(storeId, SubscriptionStatus.ACTIVE);
        if (current.isPresent() && current.get().getPaypalSubscriptionId() != null) {
            throw new IllegalStateException(
                    "You already have an active paid subscription. Cancel it before subscribing to a new plan.");
        }

        Map<String, Object> requestBody = Map.of(
                "plan_id", planId(plan),
                "custom_id", String.valueOf(storeId),
                "application_context", Map.of(
                        "brand_name", "Thuctaptotnghiep",
                        "user_action", "SUBSCRIBE_NOW",
                        "return_url", frontendUrl + "/dashboard/subscription/success",
                        "cancel_url", frontendUrl + "/dashboard/subscription/cancel"));

        Map<String, Object> response = payPalRestClient.post("/v1/billing/subscriptions", requestBody);
        String approveUrl = extractApproveLink(response);
        if (approveUrl == null) {
            throw new PayPalApiException("PayPal subscription response had no approve link: " + response);
        }

        log.info("Created PayPal subscription for store {}: plan={}, paypalSubscriptionId={}",
                storeId, plan, response.get("id"));
        return approveUrl;
    }

    /** Cancels the store's current PayPal subscription. The CANCELLED webhook flips local status. */
    @Transactional(readOnly = true)
    public void cancelSubscription(Long storeId) {
        Subscription current = subscriptionRepository
                .findFirstByStoreIdAndStatusOrderByStartDateDesc(storeId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active subscription to cancel"));
        if (current.getPaypalSubscriptionId() == null) {
            throw new IllegalStateException("A free trial isn't a PayPal subscription - nothing to cancel");
        }

        payPalRestClient.postNoContent(
                "/v1/billing/subscriptions/" + current.getPaypalSubscriptionId() + "/cancel",
                Map.of("reason", "Cancelled by store owner"));
        log.info("Requested PayPal cancellation for store {}: paypalSubscriptionId={}",
                storeId, current.getPaypalSubscriptionId());
    }

    @Transactional
    public void handleActivated(Map<String, Object> resource) {
        String paypalSubscriptionId = (String) resource.get("id");
        if (subscriptionRepository.findByPaypalSubscriptionId(paypalSubscriptionId).isPresent()) {
            log.info("Subscription {} already recorded - ignoring redelivered ACTIVATED webhook", paypalSubscriptionId);
            return;
        }

        Long storeId = extractCustomId(resource, paypalSubscriptionId);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalStateException(
                        "ACTIVATED webhook for unknown store id " + storeId + " (subscription " + paypalSubscriptionId + ")"));

        String planIdOnPayPal = (String) resource.get("plan_id");
        SubscriptionPlan plan = mapPlanId(planIdOnPayPal);

        subscriptionRepository
                .findFirstByStoreIdAndStatusOrderByStartDateDesc(storeId, SubscriptionStatus.ACTIVE)
                .ifPresent(superseded -> {
                    superseded.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptionRepository.save(superseded);
                });

        subscriptionRepository.save(Subscription.builder()
                .store(store)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(null)
                .paypalSubscriptionId(paypalSubscriptionId)
                .build());

        log.info("Subscription activated: store={}, plan={}, paypalSubscriptionId={}",
                store.getSlug(), plan, paypalSubscriptionId);
    }

    @Transactional
    public void handleCancelled(Map<String, Object> resource) {
        flipStatus(resource, SubscriptionStatus.CANCELLED);
    }

    @Transactional
    public void handleExpired(Map<String, Object> resource) {
        flipStatus(resource, SubscriptionStatus.EXPIRED);
    }

    private void flipStatus(Map<String, Object> resource, SubscriptionStatus newStatus) {
        String paypalSubscriptionId = (String) resource.get("id");
        subscriptionRepository.findByPaypalSubscriptionId(paypalSubscriptionId).ifPresentOrElse(
                subscription -> {
                    if (subscription.getStatus() == newStatus) {
                        log.info("Subscription {} already {} - ignoring redelivered webhook", paypalSubscriptionId, newStatus);
                        return;
                    }
                    subscription.setStatus(newStatus);
                    subscriptionRepository.save(subscription);
                    log.info("Subscription {} -> {}", paypalSubscriptionId, newStatus);
                },
                () -> log.warn("Webhook for unknown paypalSubscriptionId {} (target status {})", paypalSubscriptionId, newStatus));
    }

    /**
     * PAYMENT.SALE.COMPLETED also fires for every recurring subscription charge
     * (identifiable by billing_agreement_id instead of parent_payment). Payment.order
     * is mandatory (@OneToOne, nullable=false) and there's no Order for a subscription
     * charge, so no Payment row is created here - subscription payment history is a
     * separate feature, not built in this iteration. Log only.
     */
    public void handleRecurringPaymentSale(Map<String, Object> resource) {
        log.info("Recurring subscription payment: billingAgreementId={}, amount={}",
                resource.get("billing_agreement_id"), resource.get("amount"));
    }

    private String planId(SubscriptionPlan plan) {
        return switch (plan) {
            case BASIC -> basicPlanId;
            case PRO -> proPlanId;
            case FREE_TRIAL -> throw new IllegalArgumentException("FREE_TRIAL has no PayPal plan - it isn't purchasable");
        };
    }

    private SubscriptionPlan mapPlanId(String paypalPlanId) {
        if (basicPlanId.equals(paypalPlanId)) {
            return SubscriptionPlan.BASIC;
        }
        if (proPlanId.equals(paypalPlanId)) {
            return SubscriptionPlan.PRO;
        }
        throw new IllegalStateException("Unrecognized PayPal plan_id: " + paypalPlanId);
    }

    @SuppressWarnings("unchecked")
    private Long extractCustomId(Map<String, Object> resource, String paypalSubscriptionId) {
        Object customId = resource.get("custom_id");
        if (customId == null) {
            log.warn("ACTIVATED webhook missing custom_id for subscription {} - fetching subscription details", paypalSubscriptionId);
            Map<String, Object> fetched = payPalRestClient.get("/v1/billing/subscriptions/" + paypalSubscriptionId);
            customId = fetched.get("custom_id");
        }
        if (customId == null) {
            throw new IllegalStateException("Could not resolve store id (custom_id) for subscription " + paypalSubscriptionId);
        }
        return Long.valueOf(customId.toString());
    }

    @SuppressWarnings("unchecked")
    private String extractApproveLink(Map<String, Object> response) {
        List<Map<String, Object>> links = (List<Map<String, Object>>) response.get("links");
        if (links == null) {
            return null;
        }
        return links.stream()
                .filter(link -> "approve".equals(link.get("rel")))
                .map(link -> (String) link.get("href"))
                .findFirst()
                .orElse(null);
    }
}
