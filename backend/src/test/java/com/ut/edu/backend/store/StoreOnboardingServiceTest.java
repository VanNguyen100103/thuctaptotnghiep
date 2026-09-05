package com.ut.edu.backend.store;

import com.ut.edu.backend.auth.OtpService;
import com.ut.edu.backend.auth.OtpVerification;
import com.ut.edu.backend.email.EmailService;
import com.ut.edu.backend.user.Role;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreOnboardingServiceTest {

    @Mock private StoreRepository storeRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private StoreSampleDataSeeder sampleDataSeeder;
    @Mock private OtpService otpService;
    @Mock private EmailService emailService;

    @InjectMocks
    private StoreOnboardingService onboardingService;

    private RegisterStoreRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterStoreRequest();
        request.setStoreName("Shop Bán Lẻ A");
        request.setStoreSlug("shop-ban-le-a");
        request.setUsername("chushopa");
        request.setEmail("owner@shopa.vn");
        request.setPassword("Secret@123");
        request.setFirstName("Anh");
        request.setLastName("Nguyen");
    }

    @Test
    void registerStore_happyPath_createsStoreOwnerTrialAndSeedsData() {
        when(storeRepository.existsBySlug("shop-ban-le-a")).thenReturn(false);
        when(userRepository.existsByUsername("chushopa")).thenReturn(false);
        when(userRepository.existsByEmail("owner@shopa.vn")).thenReturn(false);
        when(passwordEncoder.encode("Secret@123")).thenReturn("$2a$12$encoded................................");
        when(storeRepository.save(any(Store.class))).thenAnswer(inv -> {
            Store s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpService.generateOtp(any(User.class), eq(OtpVerification.OtpType.REGISTRATION)))
                .thenReturn("123456");

        Store store = onboardingService.registerStore(request);

        assertThat(store.getSlug()).isEqualTo("shop-ban-le-a");
        assertThat(store.getStatus()).isEqualTo(StoreStatus.TRIAL);

        // Owner: disabled until OTP, linked to the store as OWNER, password encoded
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User owner = userCaptor.getValue();
        assertThat(owner.getStore()).isSameAs(store);
        assertThat(owner.getStoreRole()).isEqualTo(StoreRole.OWNER);
        assertThat(owner.getEnabled()).isFalse();
        assertThat(owner.getRoles()).containsExactly(Role.USER);
        assertThat(owner.getPassword()).startsWith("$2a$12$");

        // 14-day FREE_TRIAL subscription
        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subCaptor.capture());
        Subscription trial = subCaptor.getValue();
        assertThat(trial.getPlan()).isEqualTo(SubscriptionPlan.FREE_TRIAL);
        assertThat(trial.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(trial.getEndDate()).isEqualTo(LocalDate.now().plusDays(StoreOnboardingService.TRIAL_DAYS));

        verify(sampleDataSeeder).seed(store);
        verify(emailService).sendOtpEmail(owner, "123456");
    }

    @Test
    void registerStore_duplicateSlug_rejectsBeforeCreatingAnything() {
        when(storeRepository.existsBySlug("shop-ban-le-a")).thenReturn(true);

        assertThatThrownBy(() -> onboardingService.registerStore(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug already exists");

        verify(storeRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(subscriptionRepository, never()).save(any());
        verify(sampleDataSeeder, never()).seed(any());
    }

    @Test
    void registerStore_reservedSlug_rejected() {
        request.setStoreSlug("register");

        assertThatThrownBy(() -> onboardingService.registerStore(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");

        verifyNoInteractions(subscriptionRepository, sampleDataSeeder, emailService);
    }

    @Test
    void registerStore_duplicateUsername_rejected() {
        when(storeRepository.existsBySlug(anyString())).thenReturn(false);
        when(userRepository.existsByUsername("chushopa")).thenReturn(true);

        assertThatThrownBy(() -> onboardingService.registerStore(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");

        verify(storeRepository, never()).save(any());
    }

    @Test
    void registerStore_slugIsNormalizedToLowercase() {
        request.setStoreSlug("Shop-Ban-Le-A");
        when(storeRepository.existsBySlug("shop-ban-le-a")).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encoded................................");
        when(storeRepository.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpService.generateOtp(any(), any())).thenReturn("123456");

        Store store = onboardingService.registerStore(request);

        assertThat(store.getSlug()).isEqualTo("shop-ban-le-a");
    }
}
