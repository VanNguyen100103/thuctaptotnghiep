package com.ut.edu.backend.auth;

import com.ut.edu.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpVerificationRepository otpRepository;

    @InjectMocks
    private OtpService otpService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
    }

    @Test
    void generateOtp_returnsSixDigitCodeAndPersistsIt() {
        String code = otpService.generateOtp(user, OtpVerification.OtpType.REGISTRATION);

        assertThat(code).matches("\\d{6}");

        ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpRepository).save(captor.capture());
        OtpVerification saved = captor.getValue();
        assertThat(saved.getOtpCode()).isEqualTo(code);
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getIsUsed()).isFalse();
        assertThat(saved.getAttemptCount()).isZero();
        assertThat(saved.getExpiryDate()).isAfter(LocalDateTime.now());
    }

    @Test
    void generateOtp_invalidatesPreviousOtpsOfSameType() {
        otpService.generateOtp(user, OtpVerification.OtpType.PASSWORD_RESET);

        verify(otpRepository).deleteByUserAndOtpType(user, OtpVerification.OtpType.PASSWORD_RESET);
    }

    @Test
    void verifyOtp_unknownCode_returnsFalse() {
        when(otpRepository.findByUserAndOtpCodeAndOtpType(user, "000000", OtpVerification.OtpType.REGISTRATION))
                .thenReturn(Optional.empty());

        boolean result = otpService.verifyOtp(user, "000000", OtpVerification.OtpType.REGISTRATION);

        assertThat(result).isFalse();
    }

    @Test
    void verifyOtp_validCode_returnsTrueAndMarksUsed() {
        OtpVerification otp = validOtp("123456");
        when(otpRepository.findByUserAndOtpCodeAndOtpType(user, "123456", OtpVerification.OtpType.REGISTRATION))
                .thenReturn(Optional.of(otp));

        boolean result = otpService.verifyOtp(user, "123456", OtpVerification.OtpType.REGISTRATION);

        assertThat(result).isTrue();
        assertThat(otp.getIsUsed()).isTrue();
        assertThat(otp.getAttemptCount()).isEqualTo(1);
        verify(otpRepository, atLeastOnce()).save(any(OtpVerification.class));
    }

    @Test
    void verifyOtp_expiredCode_returnsFalse() {
        OtpVerification otp = validOtp("123456");
        otp.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        when(otpRepository.findByUserAndOtpCodeAndOtpType(user, "123456", OtpVerification.OtpType.REGISTRATION))
                .thenReturn(Optional.of(otp));

        boolean result = otpService.verifyOtp(user, "123456", OtpVerification.OtpType.REGISTRATION);

        assertThat(result).isFalse();
        assertThat(otp.getIsUsed()).isFalse();
    }

    @Test
    void verifyOtp_alreadyUsedCode_returnsFalse() {
        OtpVerification otp = validOtp("123456");
        otp.setIsUsed(true);
        when(otpRepository.findByUserAndOtpCodeAndOtpType(user, "123456", OtpVerification.OtpType.REGISTRATION))
                .thenReturn(Optional.of(otp));

        boolean result = otpService.verifyOtp(user, "123456", OtpVerification.OtpType.REGISTRATION);

        assertThat(result).isFalse();
    }

    @Test
    void verifyOtp_tooManyAttempts_returnsFalse() {
        OtpVerification otp = validOtp("123456");
        otp.setAttemptCount(5); // becomes 6 on this attempt, limit is 5
        when(otpRepository.findByUserAndOtpCodeAndOtpType(user, "123456", OtpVerification.OtpType.REGISTRATION))
                .thenReturn(Optional.of(otp));

        boolean result = otpService.verifyOtp(user, "123456", OtpVerification.OtpType.REGISTRATION);

        assertThat(result).isFalse();
        assertThat(otp.getIsUsed()).isFalse();
    }

    private OtpVerification validOtp(String code) {
        return OtpVerification.builder()
                .id(10L)
                .user(user)
                .otpCode(code)
                .otpType(OtpVerification.OtpType.REGISTRATION)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
    }
}
