package com.ut.edu.backend.repository;

import com.ut.edu.backend.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Review entity
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductIdAndApprovedTrue(Long productId, Pageable pageable);

    Page<Review> findByApprovedTrue(Pageable pageable);

    Page<Review> findByUserId(Long userId, Pageable pageable);

    /**
     * Find reviews by product ID with rating filter
     */
    Page<Review> findByProductIdAndRatingAndApprovedTrue(Long productId, Integer rating, Pageable pageable);

    /**
     * Find reviews by product ID with multiple ratings filter
     */
    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND r.rating IN :ratings AND r.approved = true")
    Page<Review> findByProductIdAndRatingInAndApprovedTrue(@Param("productId") Long productId, @Param("ratings") java.util.List<Integer> ratings, Pageable pageable);

    /**
     * Search reviews by keyword in title or comment
     */
    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND (LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.comment) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND r.approved = true")
    Page<Review> searchByProductIdAndKeyword(@Param("productId") Long productId, @Param("keyword") String keyword, Pageable pageable);

    /**
     * Search reviews by keyword and filter by ratings
     */
    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND r.rating IN :ratings AND (LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.comment) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND r.approved = true")
    Page<Review> searchByProductIdAndKeywordAndRatings(@Param("productId") Long productId, @Param("keyword") String keyword, @Param("ratings") java.util.List<Integer> ratings, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.approved = true")
    Double getAverageRatingByProductId(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.id = :productId AND r.approved = true")
    Long countByProductIdAndApprovedTrue(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.id = :productId AND r.rating = :rating AND r.approved = true")
    Long countByProductIdAndRating(@Param("productId") Long productId, @Param("rating") Integer rating);

    Boolean existsByUserIdAndProductId(Long userId, Long productId);

    // ==================== AI CLUSTERING QUERIES ====================

    /**
     * Count reviews by user for clustering
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.user = :user")
    Long countByUser(@Param("user") com.ut.edu.backend.model.User user);
}
