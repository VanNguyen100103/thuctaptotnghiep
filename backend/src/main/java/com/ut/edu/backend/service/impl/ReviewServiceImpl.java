package com.ut.edu.backend.service.impl;

import com.ut.edu.backend.dto.CreateReviewRequest;
import com.ut.edu.backend.dto.ReviewResponse;
import com.ut.edu.backend.dto.UpdateReviewRequest;
import com.ut.edu.backend.enums.OrderStatus;

import com.ut.edu.backend.model.Product;
import com.ut.edu.backend.model.Review;
import com.ut.edu.backend.model.ReviewImage;
import com.ut.edu.backend.model.User;
import com.ut.edu.backend.repository.OrderRepository;
import com.ut.edu.backend.repository.ProductRepository;
import com.ut.edu.backend.repository.ReviewImageRepository;
import com.ut.edu.backend.repository.ReviewRepository;
import com.ut.edu.backend.repository.UserRepository;
import com.ut.edu.backend.service.inter.ICloudinaryService;
import com.ut.edu.backend.service.inter.IReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Service implementation for Review management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements IReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ICloudinaryService cloudinaryService;

    @Override
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request, Long userId) {
        log.info("Creating review for product {} by user {}", request.getProductId(), userId);

        // Check if user already reviewed this product
        if (reviewRepository.existsByUserIdAndProductId(userId, request.getProductId())) {
            throw new IllegalStateException("You have already reviewed this product");
        }

        // Get product and user
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if user purchased this product (verified purchase)
        boolean verified = hasUserPurchasedProduct(userId, request.getProductId());

        // Create review
        Review review = Review.builder()
                .product(product)
                .user(user)
                .rating(request.getRating())
                .title(request.getTitle())
                .comment(request.getComment())
                .verified(verified)
                .approved(true) // Auto-approved, no admin moderation needed
                .helpfulCount(0)
                .reportCount(0)
                .build();

        Review savedReview = reviewRepository.save(review);

        // Update product rating stats immediately since review is auto-approved
        updateProductRatingStats(product.getId());

        log.info("Review created successfully with ID: {}, verified: {}, auto-approved", savedReview.getId(), verified);

        return ReviewResponse.fromEntity(savedReview);
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Long reviewId, UpdateReviewRequest request, Long userId) {
        log.info("Updating review {} by user {}", reviewId, userId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        // Check ownership
        if (!review.getUser().getId().equals(userId)) {
            throw new IllegalStateException("You can only update your own reviews");
        }

        // Update fields
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());
        // Keep approved = true (no moderation needed)

        Review updatedReview = reviewRepository.save(review);

        // Update product rating stats after review edit
        updateProductRatingStats(review.getProduct().getId());

        log.info("Review {} updated successfully", reviewId);

        return ReviewResponse.fromEntity(updatedReview);
    }

    @Override
    @Transactional
    public ReviewResponse patchReview(Long reviewId, Map<String, Object> updates, Long userId) {
        log.info("Patching review {} by user {} with updates: {}", reviewId, userId, updates.keySet());

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        // Check ownership
        if (!review.getUser().getId().equals(userId)) {
            throw new IllegalStateException("You can only update your own reviews");
        }

        // Update only provided fields
        if (updates.containsKey("rating")) {
            Integer rating = (Integer) updates.get("rating");
            if (rating < 1 || rating > 5) {
                throw new IllegalArgumentException("Rating must be between 1 and 5");
            }
            review.setRating(rating);
        }

        if (updates.containsKey("title")) {
            String title = (String) updates.get("title");
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalArgumentException("Title cannot be empty");
            }
            if (title.length() < 3 || title.length() > 200) {
                throw new IllegalArgumentException("Title must be between 3 and 200 characters");
            }
            review.setTitle(title);
        }

        if (updates.containsKey("comment")) {
            String comment = (String) updates.get("comment");
            if (comment == null || comment.trim().isEmpty()) {
                throw new IllegalArgumentException("Comment cannot be empty");
            }
            if (comment.length() < 10 || comment.length() > 2000) {
                throw new IllegalArgumentException("Comment must be between 10 and 2000 characters");
            }
            review.setComment(comment);
        }

        Review patchedReview = reviewRepository.save(review);

        // Update product rating stats if rating changed
        if (updates.containsKey("rating")) {
            updateProductRatingStats(review.getProduct().getId());
        }

        log.info("Review {} patched successfully", reviewId);

        return ReviewResponse.fromEntity(patchedReview);
    }

    @Override
    @Transactional
    public void deleteReview(Long reviewId, Long userId) {
        log.info("Deleting review {} by user {}", reviewId, userId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        // Check ownership
        if (!review.getUser().getId().equals(userId)) {
            throw new IllegalStateException("You can only delete your own reviews");
        }

        // Delete images from Cloudinary
        cloudinaryService.deleteReviewImages(reviewId);

        // Delete review (images will be cascade deleted from database due to orphanRemoval = true)
        reviewRepository.delete(review);
        log.info("Review {} and its images deleted successfully", reviewId);

        // Update product rating stats
        updateProductRatingStats(review.getProduct().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable) {
        log.info("Getting approved reviews for product {}", productId);

        return reviewRepository.findByProductIdAndApprovedTrue(productId, pageable)
                .map(ReviewResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getProductReviewsWithFilters(Long productId, String keyword, java.util.List<Integer> ratings, Pageable pageable) {
        log.info("Getting product {} reviews with filters - keyword: {}, ratings: {}", productId, keyword, ratings);

        Page<Review> reviewPage;

        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        boolean hasRatings = ratings != null && !ratings.isEmpty();

        if (hasKeyword && hasRatings) {
            // Both keyword and ratings filter
            reviewPage = reviewRepository.searchByProductIdAndKeywordAndRatings(productId, keyword.trim(), ratings, pageable);
        } else if (hasKeyword) {
            // Only keyword filter
            reviewPage = reviewRepository.searchByProductIdAndKeyword(productId, keyword.trim(), pageable);
        } else if (hasRatings) {
            // Only ratings filter
            reviewPage = reviewRepository.findByProductIdAndRatingInAndApprovedTrue(productId, ratings, pageable);
        } else {
            // No filters, return all approved reviews
            reviewPage = reviewRepository.findByProductIdAndApprovedTrue(productId, pageable);
        }

        return reviewPage.map(ReviewResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getUserReviews(Long userId, Pageable pageable) {
        log.info("Getting reviews by user {}", userId);

        return reviewRepository.findByUserId(userId, pageable)
                .map(ReviewResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReviewById(Long reviewId) {
        log.info("Getting review {}", reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        return ReviewResponse.fromEntity(review);
    }

    @Override
    @Transactional
    public void markAsHelpful(Long reviewId) {
        log.info("Marking review {} as helpful", reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        review.setHelpfulCount(review.getHelpfulCount() + 1);
        reviewRepository.save(review);

        log.info("Review {} marked as helpful, new count: {}", reviewId, review.getHelpfulCount());
    }

    @Override
    @Transactional
    public void reportReview(Long reviewId) {
        log.info("Reporting review {}", reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        review.setReportCount(review.getReportCount() + 1);
        reviewRepository.save(review);

        log.info("Review {} reported, new count: {}", reviewId, review.getReportCount());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getAllReviews(Pageable pageable) {
        log.info("Admin: Getting all reviews");

        return reviewRepository.findAll(pageable)
                .map(ReviewResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getAllApprovedReviews(Pageable pageable) {
        log.info("Getting all approved reviews");

        return reviewRepository.findByApprovedTrue(pageable)
                .map(ReviewResponse::fromEntity);
    }

    @Override
    @Transactional
    public ReviewResponse approveReview(Long reviewId) {
        log.info("Admin: Approving review {}", reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        review.setApproved(true);
        Review approvedReview = reviewRepository.save(review);

        // Update product rating stats
        updateProductRatingStats(review.getProduct().getId());

        log.info("Review {} approved successfully", reviewId);
        return ReviewResponse.fromEntity(approvedReview);
    }

    @Override
    @Transactional
    public ReviewResponse rejectReview(Long reviewId) {
        log.info("Admin: Rejecting review {}", reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        review.setApproved(false);
        Review rejectedReview = reviewRepository.save(review);

        // Update product rating stats
        updateProductRatingStats(review.getProduct().getId());

        log.info("Review {} rejected successfully", reviewId);
        return ReviewResponse.fromEntity(rejectedReview);
    }

    @Override
    @Transactional
    public void adminDeleteReview(Long reviewId) {
        log.info("Admin: Deleting review {}", reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        Long productId = review.getProduct().getId();

        // Delete images from Cloudinary
        cloudinaryService.deleteReviewImages(reviewId);

        // Delete review (images will be cascade deleted from database)
        reviewRepository.delete(review);

        // Update product rating stats
        updateProductRatingStats(productId);

        log.info("Admin: Review {} and its images deleted successfully", reviewId);
    }

    @Override
    public Object getProductRatingStats(Long productId) {
        log.info("Getting rating statistics for product {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Map<String, Object> stats = new HashMap<>();
        stats.put("productId", productId);
        stats.put("productName", product.getName());
        stats.put("averageRating", product.getAverageRating());
        stats.put("totalReviews", product.getReviewCount());

        // Rating distribution (1-5 stars)
        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            Long count = reviewRepository.countByProductIdAndRating(productId, i);
            distribution.put(i, count);
        }
        stats.put("ratingDistribution", distribution);

        // Calculate percentages
        Map<Integer, Double> percentages = new HashMap<>();
        long total = product.getReviewCount();
        if (total > 0) {
            for (int i = 1; i <= 5; i++) {
                double percentage = (distribution.get(i) * 100.0) / total;
                percentages.put(i, Math.round(percentage * 10.0) / 10.0);
            }
        }
        stats.put("ratingPercentages", percentages);

        return stats;
    }

    @Override
    @Transactional
    public ReviewResponse uploadReviewImages(Long reviewId, MultipartFile[] files, Long userId) {
        log.info("Uploading {} images for review {} by user {}", files.length, reviewId, userId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        // Check ownership
        if (!review.getUser().getId().equals(userId)) {
            throw new IllegalStateException("You can only upload images for your own reviews");
        }

        // Validate files
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No images provided");
        }

        // Limit to 5 images per review
        if (files.length > 5) {
            throw new IllegalArgumentException("Maximum 5 images allowed per review");
        }

        // Get current image count
        int currentImageCount = review.getImages() != null ? review.getImages().size() : 0;
        if (currentImageCount + files.length > 5) {
            throw new IllegalArgumentException(
                String.format("Cannot upload %d images. Review already has %d images. Maximum 5 total.",
                    files.length, currentImageCount)
            );
        }

        // Upload images to Cloudinary
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];

            try {
                // Upload to Cloudinary
                String imageUrl = cloudinaryService.uploadReviewImage(file, reviewId);

                // Extract publicId from URL
                String publicId = cloudinaryService.extractPublicIdFromUrl(imageUrl);

                // Create ReviewImage entity
                ReviewImage reviewImage = ReviewImage.builder()
                        .review(review)
                        .imageUrl(imageUrl)
                        .cloudinaryPublicId(publicId)
                        .displayOrder(currentImageCount + i)
                        .build();

                // Add to review's images list
                review.getImages().add(reviewImage);

                log.info("Successfully uploaded review image {}/{}: {}", i + 1, files.length, publicId);

            } catch (IOException e) {
                log.error("Failed to upload review image {}/{} for review {}", i + 1, files.length, reviewId, e);
                throw new RuntimeException("Failed to upload image: " + e.getMessage(), e);
            }
        }

        // Save review (cascade will save images)
        Review updatedReview = reviewRepository.save(review);

        log.info("Successfully uploaded {} images for review {}", files.length, reviewId);

        return ReviewResponse.fromEntity(updatedReview);
    }

    /**
     * Helper: Check if user has purchased this product
     */
    private boolean hasUserPurchasedProduct(Long userId, Long productId) {
        // Find orders with DELIVERED status
        return orderRepository.findByUserId(userId, Pageable.unpaged()).getContent().stream()
                .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                .flatMap(order -> order.getItems().stream())
                .anyMatch(item -> item.getProduct().getId().equals(productId));
    }

    /**
     * Helper: Update product's average rating and review count
     */
    private void updateProductRatingStats(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        // Get average rating (only approved reviews)
        Double avgRating = reviewRepository.getAverageRatingByProductId(productId);
        BigDecimal averageRating = avgRating != null ?
                BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // Get review count (only approved reviews)
        Long reviewCount = reviewRepository.countByProductIdAndApprovedTrue(productId);

        product.setAverageRating(averageRating);
        product.setReviewCount(reviewCount.intValue());

        productRepository.save(product);
        log.info("Updated product {} rating stats: avg={}, count={}",
                productId, averageRating, reviewCount);
    }
}
