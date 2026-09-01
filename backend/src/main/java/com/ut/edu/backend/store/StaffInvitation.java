package com.ut.edu.backend.store;

import com.ut.edu.backend.common.BaseEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Invitation of a staff member (MANAGER/STAFF) into a store, sent by the
 * OWNER. The invitee does not exist as a User yet - they create their
 * account by redeeming the emailed token at POST /stores/accept-invite.
 */
@Entity
@Table(name = "staff_invitations", indexes = {
    @Index(name = "idx_staff_invitation_store", columnList = "store_id"),
    @Index(name = "idx_staff_invitation_email", columnList = "email")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class StaffInvitation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    @JsonIgnore
    private Store store;

    @Column(nullable = false)
    private String email;

    /** Only MANAGER or STAFF can be invited - a store has exactly one OWNER. */
    @Enumerated(EnumType.STRING)
    @Column(name = "store_role", nullable = false, length = 20)
    private StoreRole storeRole;

    @Column(nullable = false, unique = true, length = 64)
    @JsonIgnore
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isAccepted() {
        return acceptedAt != null;
    }
}
