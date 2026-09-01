package com.ut.edu.backend.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Cart entity
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    // One cart per (user, store) - see uk_carts_store_user. A user can shop
    // multiple stores, so lookups must always be scoped to both.
    Optional<Cart> findByUserIdAndStoreId(Long userId, Long storeId);
}
