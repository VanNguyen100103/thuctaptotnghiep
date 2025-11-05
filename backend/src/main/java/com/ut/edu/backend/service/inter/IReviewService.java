package com.ut.edu.backend.service.inter;

import com.ut.edu.backend.dto.CreateReviewRequest;
import com.ut.edu.backend.dto.ReviewResponse;
import com.ut.edu.backend.dto.UpdateReviewRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Service interface for Review management
 */
public interface IReviewService {

    /**
     * Create a new review for a product
     * User can only review a product once
     * Review needs admin approval before showing publicly
     */
    ReviewResponse createReview(CreateReviewRequest request, Long userId);

    /**
     * Update an existing review
     * User can only update their own reviews
     */
    ReviewResponse updateReview(Long reviewId, UpdateReviewRequest request, Long userId);

    /**
     * Partially update an existing review (PATCH)
     * Accepts Map to allow updating only specific fields
     * User can only update their own reviews
     */
    ReviewResponse patchReview(Long reviewId, Map<String, Object> updates, Long userId);

    /**
     * Delete a review
     * User can only delete their own reviews
     */
    void deleteReview(Long reviewId, Long userId);

    /**
     * Get all approved reviews for a product
     */
    Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable);

    /**
     * Get product reviews with search and rating filter
     * @param productId Product ID
     * @param keyword Search keyword (optional, can be null or empty)
     * @param ratings List of ratings to filter (optional, can be null or empty)
     * @param pageable Pagination info
     * @return Page of filtered reviews
     */
    Page<ReviewResponse> getProductReviewsWithFilters(Long productId, String keyword, java.util.List<Integer> ratings, Pageable pageable);

    /**
     * Get all reviews by a user
     */
    Page<ReviewResponse> getUserReviews(Long userId, Pageable pageable);

    /**
     * Get a single review by ID
     */
    ReviewResponse getReviewById(Long reviewId);

    /**
     * Mark a review as helpful
     */
    void markAsHelpful(Long reviewId);

    /**
     * Report a review
     */
    void reportReview(Long reviewId);

    /**
     * Get all approved reviews (public endpoint)
     */
    Page<ReviewResponse> getAllApprovedReviews(Pageable pageable);

    /**
     * Admin: Get all reviews (including unapproved)
     */
    Page<ReviewResponse> getAllReviews(Pageable pageable);

    /**
     * Admin: Approve a review
     */
    ReviewResponse approveReview(Long reviewId);

    /**
     * Admin: Reject/Hide a review
     */
    ReviewResponse rejectReview(Long reviewId);

    /**
     * Admin: Delete any review
     */
    void adminDeleteReview(Long reviewId);

    /**
     * Get product rating statistics
     */
    Object getProductRatingStats(Long productId);

    /**
     * Upload images for a review
     * User can only upload images for their own reviews
     */
    ReviewResponse uploadReviewImages(Long reviewId, MultipartFile[] files, Long userId);
}
