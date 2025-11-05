package com.ut.edu.backend.repository;

import com.ut.edu.backend.model.ProductView;

import com.ut.edu.backend.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ProductView entity
 * Provides queries for both authenticated and anonymous user view history
 */
@Repository
public interface ProductViewRepository extends JpaRepository<ProductView, Long> {

    /**
     * Find recently viewed products for authenticated user
     * Returns product views ordered by most recent view
     * Uses DISTINCT to ensure each product appears only once
     */
    @Query("SELECT pv FROM ProductView pv " +
           "WHERE pv.user.id = :userId " +
           "AND pv.id IN (" +
           "  SELECT MAX(pv2.id) FROM ProductView pv2 " +
           "  WHERE pv2.user.id = :userId " +
           "  GROUP BY pv2.product.id" +
           ") " +
           "ORDER BY pv.viewedAt DESC")
    List<ProductView> findRecentlyViewedByUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * Find recently viewed products for anonymous session
     * Returns product views ordered by most recent view
     * Uses DISTINCT to ensure each product appears only once
     */
    @Query("SELECT pv FROM ProductView pv " +
           "WHERE pv.sessionId = :sessionId " +
           "AND pv.id IN (" +
           "  SELECT MAX(pv2.id) FROM ProductView pv2 " +
           "  WHERE pv2.sessionId = :sessionId " +
           "  GROUP BY pv2.product.id" +
           ") " +
           "ORDER BY pv.viewedAt DESC")
    List<ProductView> findRecentlyViewedBySession(@Param("sessionId") String sessionId, Pageable pageable);

    /**
     * Find existing view record for user and product
     */
    @Query("SELECT pv FROM ProductView pv " +
           "WHERE pv.user.id = :userId AND pv.product.id = :productId " +
           "ORDER BY pv.viewedAt DESC")
    Optional<ProductView> findByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    /**
     * Find existing view record for session and product
     */
    @Query("SELECT pv FROM ProductView pv " +
           "WHERE pv.sessionId = :sessionId AND pv.product.id = :productId " +
           "ORDER BY pv.viewedAt DESC")
    Optional<ProductView> findBySessionAndProduct(@Param("sessionId") String sessionId, @Param("productId") Long productId);

    /**
     * Count total views for a user
     */
    @Query("SELECT COUNT(DISTINCT pv.product) FROM ProductView pv WHERE pv.user.id = :userId")
    Long countByUser(@Param("userId") Long userId);

    /**
     * Count total views for a session
     */
    @Query("SELECT COUNT(DISTINCT pv.product) FROM ProductView pv WHERE pv.sessionId = :sessionId")
    Long countBySession(@Param("sessionId") String sessionId);

    /**
     * Delete old view records for a user (keep only recent N records)
     */
    @Modifying
    @Query("DELETE FROM ProductView pv WHERE pv.user.id = :userId " +
           "AND pv.id NOT IN (" +
           "  SELECT pv2.id FROM ProductView pv2 " +
           "  WHERE pv2.user.id = :userId " +
           "  ORDER BY pv2.viewedAt DESC " +
           "  LIMIT :keepCount" +
           ")")
    void deleteOldUserViews(@Param("userId") Long userId, @Param("keepCount") int keepCount);

    /**
     * Delete old view records for a session (keep only recent N records)
     */
    @Modifying
    @Query("DELETE FROM ProductView pv WHERE pv.sessionId = :sessionId " +
           "AND pv.id NOT IN (" +
           "  SELECT pv2.id FROM ProductView pv2 " +
           "  WHERE pv2.sessionId = :sessionId " +
           "  ORDER BY pv2.viewedAt DESC " +
           "  LIMIT :keepCount" +
           ")")
    void deleteOldSessionViews(@Param("sessionId") String sessionId, @Param("keepCount") int keepCount);

    /**
     * Delete views older than specified date (cleanup task)
     */
    @Modifying
    @Query("DELETE FROM ProductView pv WHERE pv.viewedAt < :cutoffDate")
    void deleteViewsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find all views by user
     */
    List<ProductView> findByUserOrderByViewedAtDesc(User user);

    /**
     * Find all views by session
     */
    List<ProductView> findBySessionIdOrderByViewedAtDesc(String sessionId);

    /**
     * Migrate anonymous views to authenticated user when user logs in
     */
    @Modifying
    @Query("UPDATE ProductView pv SET pv.user = :user, pv.sessionId = null " +
           "WHERE pv.sessionId = :sessionId")
    void migrateSessionViewsToUser(@Param("sessionId") String sessionId, @Param("user") User user);
}
