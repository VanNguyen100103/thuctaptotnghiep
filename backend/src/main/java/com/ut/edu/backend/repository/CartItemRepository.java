package com.ut.edu.backend.repository;

import com.ut.edu.backend.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for CartItem entity
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartId(Long cartId);

    /**
     * Find cart item by product, size, and color
     * This ensures each combination is unique in the cart
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.id = :cartId " +
           "AND ci.product.id = :productId " +
           "AND ci.selectedSize = :size " +
           "AND ci.selectedColor = :color")
    Optional<CartItem> findByCartIdAndProductIdAndSizeAndColor(
        @Param("cartId") Long cartId,
        @Param("productId") Long productId,
        @Param("size") String size,
        @Param("color") String color
    );

    /**
     * Check if cart item exists with same product, size, color
     */
    @Query("SELECT COUNT(ci) > 0 FROM CartItem ci WHERE ci.cart.id = :cartId " +
           "AND ci.product.id = :productId " +
           "AND ci.selectedSize = :size " +
           "AND ci.selectedColor = :color")
    boolean existsByCartIdAndProductIdAndSizeAndColor(
        @Param("cartId") Long cartId,
        @Param("productId") Long productId,
        @Param("size") String size,
        @Param("color") String color
    );

    @Modifying
    @Transactional
    void deleteByCartId(Long cartId);
}
