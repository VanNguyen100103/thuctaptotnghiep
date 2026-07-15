package com.ut.edu.backend.wishlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Wishlist entity
 */
@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    /**
     * Find all wishlist items for a specific user
     */
    List<Wishlist> findByUserId(Long userId);

    /**
     * Find wishlist item by user and product
     */
    Optional<Wishlist> findByUserIdAndProductId(Long userId, Long productId);

    /**
     * Check if user has product in wishlist
     */
    Boolean existsByUserIdAndProductId(Long userId, Long productId);

    /**
     * Delete wishlist item by user and product
     */
    void deleteByUserIdAndProductId(Long userId, Long productId);

    /**
     * Count wishlist items for a specific user
     */
    Long countByUserId(Long userId);

    /**
     * Find wishlist items for a user with product details
     */
    @Query("SELECT w FROM Wishlist w JOIN FETCH w.product p LEFT JOIN FETCH p.images WHERE w.user.id = :userId")
    List<Wishlist> findByUserIdWithProductDetails(@Param("userId") Long userId);

    /**
     * Delete all wishlist items for a specific user
     */
    void deleteByUserId(Long userId);
}
