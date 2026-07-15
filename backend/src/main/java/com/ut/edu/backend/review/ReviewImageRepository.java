package com.ut.edu.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for ReviewImage entity
 */
@Repository
public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {

    /**
     * Find all images for a specific review
     */
    List<ReviewImage> findByReviewIdOrderByDisplayOrderAsc(Long reviewId);
}
