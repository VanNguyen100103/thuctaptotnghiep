package com.ut.edu.backend.store;

import com.ut.edu.backend.email.EmailService;
import com.ut.edu.backend.user.Role;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Staff management: the OWNER invites MANAGER/STAFF members by email;
 * the invitee redeems the token to create their account inside the store.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreStaffService {

    public static final int INVITE_EXPIRY_DAYS = 7;

    private final StaffInvitationRepository invitationRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SubscriptionGuard subscriptionGuard;

    /** Result of a redeemed invitation, resolved inside the transaction. */
    public record AcceptInviteResult(String username, String storeName, String storeSlug, StoreRole storeRole) {}

    @Transactional
    public StaffInvitation invite(Long storeId, InviteStaffRequest request) {
        if (request.getStoreRole() != StoreRole.MANAGER && request.getStoreRole() != StoreRole.STAFF) {
            throw new IllegalArgumentException("Only MANAGER or STAFF can be invited");
        }

        long currentStaff = userRepository.countByStoreIdAndStoreRoleIn(
                storeId, List.of(StoreRole.MANAGER, StoreRole.STAFF));
        subscriptionGuard.requireCanAddStaff(storeId, currentStaff);

        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already has an account: " + email);
        }

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        // Re-inviting the same email refreshes the pending invitation (new token + expiry)
        StaffInvitation invitation = invitationRepository
                .findByStoreIdAndEmailAndAcceptedAtIsNull(storeId, email)
                .orElseGet(() -> StaffInvitation.builder().store(store).email(email).build());
        invitation.setStoreRole(request.getStoreRole());
        invitation.setToken(UUID.randomUUID().toString());
        invitation.setExpiresAt(LocalDateTime.now().plusDays(INVITE_EXPIRY_DAYS));
        invitation = invitationRepository.save(invitation);

        emailService.sendStaffInvitationEmail(
                email, store.getName(), request.getStoreRole().name(), invitation.getToken());

        log.info("Staff invitation sent: store={}, email={}, role={}",
                store.getSlug(), email, request.getStoreRole());
        return invitation;
    }

    @Transactional(readOnly = true)
    public List<StaffInvitation> listInvitations(Long storeId) {
        return invitationRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
    }

    @Transactional
    public AcceptInviteResult acceptInvite(AcceptInviteRequest request) {
        StaffInvitation invitation = invitationRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token"));

        if (invitation.isAccepted()) {
            throw new IllegalArgumentException("Invitation has already been used");
        }
        if (invitation.isExpired()) {
            throw new IllegalArgumentException("Invitation has expired - ask the owner to send a new one");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(invitation.getEmail())) {
            throw new IllegalArgumentException("Email already has an account: " + invitation.getEmail());
        }

        User staff = User.builder()
                .username(request.getUsername())
                .email(invitation.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .roles(new HashSet<>())
                .enabled(true) // Owning the emailed token proves control of the mailbox
                .store(invitation.getStore())
                .storeRole(invitation.getStoreRole())
                .build();
        staff.addRole(Role.USER);
        staff = userRepository.save(staff);

        invitation.setAcceptedAt(LocalDateTime.now());

        Store store = invitation.getStore();
        log.info("Staff invitation accepted: store={}, username={}, role={}",
                store.getSlug(), staff.getUsername(), staff.getStoreRole());
        return new AcceptInviteResult(
                staff.getUsername(), store.getName(), store.getSlug(), staff.getStoreRole());
    }
}
