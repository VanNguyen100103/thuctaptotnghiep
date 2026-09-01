package com.ut.edu.backend.store;

import com.ut.edu.backend.security.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Staff management inside the store dashboard.
 * OWNER only - a MANAGER cannot invite or list staff.
 */
@RestController
@RequestMapping("/store/staff")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
@Slf4j
public class StoreStaffController {

    private final StoreStaffService staffService;

    /**
     * Invite a MANAGER/STAFF member by email
     * POST /api/store/staff/invite
     */
    @PostMapping("/invite")
    public ResponseEntity<?> inviteStaff(@AuthenticationPrincipal UserPrincipal principal,
                                         @Valid @RequestBody InviteStaffRequest request) {
        try {
            StaffInvitation invitation = staffService.invite(principal.getStoreId(), request);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Invitation sent to " + invitation.getEmail(),
                    "email", invitation.getEmail(),
                    "storeRole", invitation.getStoreRole(),
                    "expiresAt", invitation.getExpiresAt()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Invitations of my store (pending + accepted)
     * GET /api/store/staff/invitations
     */
    @GetMapping("/invitations")
    public ResponseEntity<?> listInvitations(@AuthenticationPrincipal UserPrincipal principal) {
        List<StaffInvitation> invitations = staffService.listInvitations(principal.getStoreId());
        return ResponseEntity.ok(Map.of("invitations", invitations, "count", invitations.size()));
    }
}
