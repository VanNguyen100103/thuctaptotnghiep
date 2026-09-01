package com.ut.edu.backend.store;

import com.ut.edu.backend.auth.OtpService;
import com.ut.edu.backend.auth.OtpVerification;
import com.ut.edu.backend.email.EmailService;
import com.ut.edu.backend.user.Role;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
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

    @Transactional
    public Store registerStore(RegisterStoreRequest request) {
        String slug = request.getStoreSlug().trim().toLowerCase();

        if (RESERVED_SLUGS.contains(slug)) {
            throw new IllegalArgumentException("Store slug is reserved: " + slug);
        }
        if (storeRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Store slug already exists: " + slug);
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        Store store = storeRepository.save(Store.builder()
                .name(request.getStoreName().trim())
                .slug(slug)
                .phone(request.getStorePhone())
                .address(request.getStoreAddress())
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
}
