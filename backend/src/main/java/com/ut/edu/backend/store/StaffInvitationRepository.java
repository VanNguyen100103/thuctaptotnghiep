package com.ut.edu.backend.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, Long> {

    Optional<StaffInvitation> findByToken(String token);

    Optional<StaffInvitation> findByStoreIdAndEmailAndAcceptedAtIsNull(Long storeId, String email);

    List<StaffInvitation> findByStoreIdOrderByCreatedAtDesc(Long storeId);
}
