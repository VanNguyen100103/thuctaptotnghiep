package com.ut.edu.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Address entity
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    /**
     * Find all addresses for a specific user
     */
    List<Address> findByUserId(Long userId);

    /**
     * Find default address for a specific user and type
     */
    Optional<Address> findByUserIdAndIsDefaultTrueAndType(Long userId, AddressType type);

    /**
     * Find default shipping address for a user
     */
    default Optional<Address> findDefaultShippingAddress(Long userId) {
        return findByUserIdAndIsDefaultTrueAndType(userId, AddressType.SHIPPING);
    }

    /**
     * Find default billing address for a user
     */
    default Optional<Address> findDefaultBillingAddress(Long userId) {
        return findByUserIdAndIsDefaultTrueAndType(userId, AddressType.BILLING);
    }

    /**
     * Check if user has any addresses
     */
    Boolean existsByUserId(Long userId);

    /**
     * Count addresses for a specific user
     */
    Long countByUserId(Long userId);
}
