package com.ut.edu.backend.store;

import com.ut.edu.backend.auth.OtpService;
import com.ut.edu.backend.auth.OtpVerification;
import com.ut.edu.backend.auth.OtpVerificationRepository;
import com.ut.edu.backend.email.EmailService;
import com.ut.edu.backend.user.Role;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Store onboarding: one call creates the tenant (Store), its OWNER account
 * and a FREE_TRIAL subscription, then seeds demo data - all in a single
 * transaction so a failure at any step leaves nothing behind.
 *
 * The owner verifies their email through the existing OTP flow
 * (/auth/verify-otp), exactly like a regular customer registration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreOnboardingService {

    public static final int TRIAL_DAYS = 14;

    /**
     * Slugs that collide with literal /stores/... API routes and therefore
     * can never be used as a storefront address.
     */
    private static final Set<String> RESERVED_SLUGS = Set.of("register", "accept-invite");

    private final StoreRepository storeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StoreSampleDataSeeder sampleDataSeeder;
    private final OtpService otpService;
    private final EmailService emailService;
    private final OtpVerificationRepository otpVerificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Store registerStore(RegisterStoreRequest request) {
        String slug = request.getStoreSlug().trim().toLowerCase();

        if (RESERVED_SLUGS.contains(slug)) {
            throw new IllegalArgumentException("Store slug is reserved: " + slug);
        }

        // A previous registration attempt for this email may have never been
        // verified (OTP email lost/expired, user abandoned the flow) - such
        // a row would otherwise block this email from ever registering
        // again. Wipe it and let this submission start fresh, but only once
        // its own OTP window has actually lapsed, so a still-pending
        // registration can't be hijacked by someone resubmitting the form.
        Optional<User> staleOwner = userRepository.findByEmail(request.getEmail());
        if (staleOwner.isPresent()) {
            User existing = staleOwner.get();
            if (Boolean.TRUE.equals(existing.getEnabled())) {
                throw new IllegalArgumentException("Email already exists: " + request.getEmail());
            }
            boolean otpStillPending = otpVerificationRepository
                    .findTopByUserAndOtpTypeOrderByCreatedAtDesc(existing, OtpVerification.OtpType.REGISTRATION)
                    .map(OtpVerification::isValid)
                    .orElse(false);
            if (otpStillPending) {
                throw new IllegalArgumentException(
                        "A registration for this email is still pending verification. "
                                + "Check your inbox or wait for the code to expire before retrying.");
            }
            deleteStaleUnverifiedRegistration(existing);
        }

        if (storeRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Store slug already exists: " + slug);
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }

        Store store = storeRepository.save(Store.builder()
                .name(request.getStoreName().trim())
                .slug(slug)
                .phone(request.getStorePhone())
                .address(request.getStoreAddress())
                .industry(request.getStoreIndustry())
                .status(StoreStatus.TRIAL)
                .build());

        User owner = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .roles(new HashSet<>())
                .enabled(false) // Disabled until email verification, same as customer registration
                .store(store)
                .storeRole(StoreRole.OWNER)
                .build();
        owner.addRole(Role.USER);
        owner = userRepository.save(owner);

        subscriptionRepository.save(Subscription.builder()
                .store(store)
                .plan(SubscriptionPlan.FREE_TRIAL)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(TRIAL_DAYS))
                .build());

        sampleDataSeeder.seed(store);

        log.info("Store onboarded: {} (owner: {}, trial until {})",
                slug, owner.getUsername(), LocalDate.now().plusDays(TRIAL_DAYS));

        // Same verification UX as customer registration: OTP emailed, owner
        // confirms via POST /auth/verify-otp before logging in
        String otpCode = otpService.generateOtp(owner, OtpVerification.OtpType.REGISTRATION);
        emailService.sendOtpEmail(owner, otpCode);

        return store;
    }

    /**
     * Removes a never-verified owner account left over from an abandoned
     * registration, plus the store (and everything seeded into it) if this
     * user owned one. Neither the DB nor the JPA mappings define cascade
     * deletes for stores/users, so every dependent table is cleared
     * explicitly and in FK-safe order before the parent row goes.
     */
    private void deleteStaleUnverifiedRegistration(User staleUser) {
        Long userId = staleUser.getId();
        Long storeId = staleUser.getStoreRole() == StoreRole.OWNER && staleUser.getStore() != null
                ? staleUser.getStore().getId()
                : null;

        log.info("Removing stale unverified registration: user {}{}",
                userId, storeId != null ? " and owned store " + storeId : "");

        deleteUserOwnRows(userId);
        nativeUpdate("DELETE FROM users WHERE id = :id", userId);

        if (storeId != null) {
            deleteStoreOwnedRows(storeId);
        }

        entityManager.flush();
    }

    private void deleteUserOwnRows(Long userId) {
        nativeUpdate("DELETE FROM backup_codes WHERE two_factor_auth_id IN " +
                "(SELECT id FROM two_factor_auth WHERE user_id = :id)", userId);
        nativeUpdate("DELETE FROM two_factor_auth WHERE user_id = :id", userId);
        nativeUpdate("DELETE FROM review_images WHERE review_id IN " +
                "(SELECT id FROM reviews WHERE user_id = :id)", userId);
        nativeUpdate("DELETE FROM reviews WHERE user_id = :id", userId);
        nativeUpdate("DELETE FROM cart_items WHERE cart_id IN " +
                "(SELECT id FROM carts WHERE user_id = :id)", userId);
        nativeUpdate("DELETE FROM carts WHERE user_id = :id", userId);
        nativeUpdate("DELETE FROM order_items WHERE order_id IN " +
                "(SELECT id FROM orders WHERE user_id = :id)", userId);
        nativeUpdate("DELETE FROM orders WHERE user_id = :id", userId);
        nativeUpdate("DELETE FROM otp_verifications WHERE user_id = :id", userId);
        nativeUpdate("DELETE FROM verification_tokens WHERE user_id = :id", userId);
        nativeUpdate("DELETE FROM addresses WHERE user_id = :id", userId);
        nativeUpdate("DELETE FROM coupon_usages WHERE user_id = :id", userId);
        nativeUpdate("DELETE FROM product_views WHERE user_id = :id", userId);
        nativeUpdate("DELETE FROM wishlists WHERE user_id = :id", userId);
        nativeUpdate("DELETE FROM user_roles WHERE user_id = :id", userId);
        nativeUpdate("UPDATE purchase_orders SET created_by_id = NULL WHERE created_by_id = :id", userId);
        nativeUpdate("UPDATE purchase_orders SET completed_by_id = NULL WHERE completed_by_id = :id", userId);
        nativeUpdate("UPDATE sales SET created_by_id = NULL WHERE created_by_id = :id", userId);
        nativeUpdate("UPDATE ghn_shipments SET created_by_id = NULL WHERE created_by_id = :id", userId);
    }

    private void deleteStoreOwnedRows(Long storeId) {
        nativeUpdate("DELETE FROM staff_invitations WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM subscriptions WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM coupon_usages WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM coupons WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM payments WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM order_items WHERE order_id IN " +
                "(SELECT id FROM orders WHERE store_id = :id)", storeId);
        nativeUpdate("DELETE FROM orders WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM cart_items WHERE cart_id IN " +
                "(SELECT id FROM carts WHERE store_id = :id)", storeId);
        nativeUpdate("DELETE FROM carts WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM wishlists WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM product_views WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM product_images WHERE product_id IN " +
                "(SELECT id FROM products WHERE store_id = :id)", storeId);
        nativeUpdate("DELETE FROM product_sizes WHERE product_id IN " +
                "(SELECT id FROM products WHERE store_id = :id)", storeId);
        nativeUpdate("DELETE FROM product_colors WHERE product_id IN " +
                "(SELECT id FROM products WHERE store_id = :id)", storeId);
        nativeUpdate("DELETE FROM products WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM categories WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM sale_items WHERE sale_id IN " +
                "(SELECT id FROM sales WHERE store_id = :id)", storeId);
        nativeUpdate("DELETE FROM sales WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM purchase_order_items WHERE purchase_order_id IN " +
                "(SELECT id FROM purchase_orders WHERE store_id = :id)", storeId);
        nativeUpdate("DELETE FROM purchase_orders WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM suppliers WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM customers WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM ghn_shipments WHERE store_id = :id", storeId);
        nativeUpdate("DELETE FROM stores WHERE id = :id", storeId);
    }

    private int nativeUpdate(String sql, Long id) {
        return entityManager.createNativeQuery(sql).setParameter("id", id).executeUpdate();
    }
}
