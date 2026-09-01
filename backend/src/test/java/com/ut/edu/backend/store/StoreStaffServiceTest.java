package com.ut.edu.backend.store;

import com.ut.edu.backend.email.EmailService;
import com.ut.edu.backend.exception.SubscriptionRequiredException;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreStaffServiceTest {

    @Mock private StaffInvitationRepository invitationRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private UserRepository userRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private SubscriptionGuard subscriptionGuard;

    @InjectMocks
    private StoreStaffService staffService;

    private Store store;

    @BeforeEach
    void setUp() {
        store = Store.builder().id(10L).name("Shop A").slug("shop-a").status(StoreStatus.ACTIVE).build();
    }

    @Test
    void invite_happyPath_savesInvitationAndSendsEmail() {
        when(userRepository.existsByEmail("staff@shopa.vn")).thenReturn(false);
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        when(invitationRepository.findByStoreIdAndEmailAndAcceptedAtIsNull(10L, "staff@shopa.vn"))
                .thenReturn(Optional.empty());
        when(invitationRepository.save(any(StaffInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

        StaffInvitation invitation = staffService.invite(10L,
                new InviteStaffRequest("Staff@ShopA.vn", StoreRole.STAFF));

        assertThat(invitation.getEmail()).isEqualTo("staff@shopa.vn"); // normalized
        assertThat(invitation.getToken()).isNotBlank();
        assertThat(invitation.getExpiresAt()).isAfter(LocalDateTime.now());
        verify(emailService).sendStaffInvitationEmail(
                eq("staff@shopa.vn"), eq("Shop A"), eq("STAFF"), eq(invitation.getToken()));
    }

    @Test
    void invite_ownerRole_rejected() {
        assertThatThrownBy(() -> staffService.invite(10L,
                new InviteStaffRequest("x@y.vn", StoreRole.OWNER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only MANAGER or STAFF");

        verifyNoInteractions(invitationRepository, emailService);
    }

    @Test
    void invite_atStaffLimit_rejectedWithSubscriptionRequiredException() {
        when(userRepository.countByStoreIdAndStoreRoleIn(10L, List.of(StoreRole.MANAGER, StoreRole.STAFF)))
                .thenReturn(1L);
        doThrow(new SubscriptionRequiredException("Your BASIC plan allows up to 1 staff member(s). Upgrade to add more."))
                .when(subscriptionGuard).requireCanAddStaff(10L, 1L);

        assertThatThrownBy(() -> staffService.invite(10L,
                new InviteStaffRequest("x@y.vn", StoreRole.STAFF)))
                .isInstanceOf(SubscriptionRequiredException.class)
                .hasMessageContaining("Upgrade to add more");

        verifyNoInteractions(invitationRepository, emailService);
    }

    @Test
    void invite_existingUserEmail_rejected() {
        when(userRepository.existsByEmail("taken@y.vn")).thenReturn(true);

        assertThatThrownBy(() -> staffService.invite(10L,
                new InviteStaffRequest("taken@y.vn", StoreRole.STAFF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already has an account");
    }

    @Test
    void invite_pendingInviteForSameEmail_isRefreshedNotDuplicated() {
        StaffInvitation pending = StaffInvitation.builder()
                .id(5L).store(store).email("staff@shopa.vn")
                .storeRole(StoreRole.STAFF).token("old-token")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        when(userRepository.existsByEmail("staff@shopa.vn")).thenReturn(false);
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        when(invitationRepository.findByStoreIdAndEmailAndAcceptedAtIsNull(10L, "staff@shopa.vn"))
                .thenReturn(Optional.of(pending));
        when(invitationRepository.save(any(StaffInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

        StaffInvitation refreshed = staffService.invite(10L,
                new InviteStaffRequest("staff@shopa.vn", StoreRole.MANAGER));

        assertThat(refreshed.getId()).isEqualTo(5L); // same row, refreshed
        assertThat(refreshed.getToken()).isNotEqualTo("old-token");
        assertThat(refreshed.getStoreRole()).isEqualTo(StoreRole.MANAGER);
        assertThat(refreshed.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void acceptInvite_happyPath_createsEnabledStaffUserAndMarksInvitationUsed() {
        StaffInvitation invitation = StaffInvitation.builder()
                .id(5L).store(store).email("staff@shopa.vn")
                .storeRole(StoreRole.STAFF).token("valid-token")
                .expiresAt(LocalDateTime.now().plusDays(3))
                .build();
        when(invitationRepository.findByToken("valid-token")).thenReturn(Optional.of(invitation));
        when(userRepository.existsByUsername("staffuser")).thenReturn(false);
        when(userRepository.existsByEmail("staff@shopa.vn")).thenReturn(false);
        when(passwordEncoder.encode("Secret@123")).thenReturn("$2a$12$encoded................................");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AcceptInviteRequest request = new AcceptInviteRequest(
                "valid-token", "staffuser", "Secret@123", "Binh", "Tran", null);
        StoreStaffService.AcceptInviteResult result = staffService.acceptInvite(request);

        assertThat(result.username()).isEqualTo("staffuser");
        assertThat(result.storeSlug()).isEqualTo("shop-a");
        assertThat(result.storeRole()).isEqualTo(StoreRole.STAFF);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User staff = captor.getValue();
        assertThat(staff.getEnabled()).isTrue(); // token proves mailbox ownership
        assertThat(staff.getStore()).isSameAs(store);
        assertThat(staff.getStoreRole()).isEqualTo(StoreRole.STAFF);
        assertThat(staff.getEmail()).isEqualTo("staff@shopa.vn");

        assertThat(invitation.getAcceptedAt()).isNotNull();
    }

    @Test
    void acceptInvite_expiredToken_rejected() {
        StaffInvitation invitation = StaffInvitation.builder()
                .store(store).email("staff@shopa.vn").storeRole(StoreRole.STAFF)
                .token("expired-token").expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(invitationRepository.findByToken("expired-token")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> staffService.acceptInvite(new AcceptInviteRequest(
                "expired-token", "staffuser", "Secret@123", "Binh", "Tran", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");

        verify(userRepository, never()).save(any());
    }

    @Test
    void acceptInvite_alreadyUsedToken_rejected() {
        StaffInvitation invitation = StaffInvitation.builder()
                .store(store).email("staff@shopa.vn").storeRole(StoreRole.STAFF)
                .token("used-token").expiresAt(LocalDateTime.now().plusDays(1))
                .acceptedAt(LocalDateTime.now().minusHours(2))
                .build();
        when(invitationRepository.findByToken("used-token")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> staffService.acceptInvite(new AcceptInviteRequest(
                "used-token", "other", "Secret@123", "Binh", "Tran", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been used");

        verify(userRepository, never()).save(any());
    }

    @Test
    void acceptInvite_unknownToken_rejected() {
        when(invitationRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.acceptInvite(new AcceptInviteRequest(
                "nope", "staffuser", "Secret@123", "Binh", "Tran", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid invitation token");
    }
}
