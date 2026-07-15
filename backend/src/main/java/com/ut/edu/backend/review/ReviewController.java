package com.ut.edu.backend.review;

import com.ut.edu.backend.user.User;

import com.ut.edu.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Review Controller
 * Handles product reviews and ratings
 */
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Create a new review
     * POST /api/reviews
     *
     * REQUEST BODY:
     * {
     *   "productId": 13,
     *   "rating": 5,
     *   "title": "Sản phẩm tuyệt vời!",
     *   "comment": "Áo rất đẹp, chất liệu cotton mát mẻ, form chuẩn. Tôi rất hài lòng với sản phẩm này."
     * }
     *
     * RESPONSE (201 Created):
     * {
     *   "id": 1,
     *   "productId": 13,
     *   "productName": "Áo thun nam cổ tròn basic",
     *   "userId": 2,
     *   "username": "john_doe",
     *   "userAvatarUrl": "https://...",
     *   "rating": 5,
     *   "title": "Sản phẩm tuyệt vời!",
     *   "comment": "Áo rất đẹp, chất liệu cotton mát mẻ...",
     *   "verified": true,
     *   "approved": false,
     *   "helpfulCount": 0,
     *   "reportCount": 0,
     *   "createdAt": "2025-10-25T10:30:00",
     *   "updatedAt": "2025-10-25T10:30:00"
     * }
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewResponse> createReview(@Valid @RequestBody CreateReviewRequest request) {
        Long userId = getCurrentUserId();
        log.info("User {} creating review for product {}", userId, request.getProductId());

        ReviewResponse review = reviewService.createReview(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    /**
     * Update an existing review
     * PUT /api/reviews/{reviewId}
     *
     * REQUEST BODY:
     * {
     *   "rating": 4,
     *   "title": "Sản phẩm tốt nhưng hơi nhỏ",
     *   "comment": "Sau khi mặc thử thì thấy chất liệu tốt nhưng size hơi nhỏ. Nên mua size lớn hơn 1 size."
     * }
     *
     * RESPONSE (200 OK):
     * {
     *   "id": 1,
     *   "productId": 13,
     *   "productName": "Áo thun nam cổ tròn basic",
     *   "userId": 2,
     *   "username": "john_doe",
     *   "rating": 4,
     *   "title": "Sản phẩm tốt nhưng hơi nhỏ",
     *   "comment": "Sau khi mặc thử...",
     *   "verified": true,
     *   "approved": false,
     *   "helpfulCount": 5,
     *   "reportCount": 0,
     *   "createdAt": "2025-10-25T10:30:00",
     *   "updatedAt": "2025-10-25T11:45:00"
     * }
     */
    @PutMapping("/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody UpdateReviewRequest request) {
        Long userId = getCurrentUserId();
        log.info("User {} updating review {}", userId, reviewId);

        ReviewResponse review = reviewService.updateReview(reviewId, request, userId);
        return ResponseEntity.ok(review);
    }

    /**
     * Partially update a review (flexible update)
     * PATCH /api/reviews/{reviewId}
     *
     * REQUEST BODY (send only fields you want to update):
     *
     * Example 1 - Update only rating:
     * {
     *   "rating": 5
     * }
     *
     * Example 2 - Update rating and title:
     * {
     *   "rating": 4,
     *   "title": "Cập nhật: Sản phẩm khá ổn"
     * }
     *
     * Example 3 - Update all fields:
     * {
     *   "rating": 3,
     *   "title": "Bình thường thôi",
     *   "comment": "Chất liệu tạm được nhưng không xuất sắc lắm"
     * }
     *
     * RESPONSE (200 OK):
     * {
     *   "id": 1,
     *   "productId": 13,
     *   "productName": "Áo thun nam cổ tròn basic",
     *   "userId": 2,
     *   "username": "john_doe",
     *   "rating": 5,
     *   "title": "Sản phẩm tuyệt vời!",
     *   "comment": "Áo rất đẹp...",
     *   "verified": true,
     *   "approved": true,
     *   "helpfulCount": 5,
     *   "reportCount": 0,
     *   "createdAt": "2025-10-25T10:30:00",
     *   "updatedAt": "2025-10-25T12:00:00"
     * }
     */
    @PatchMapping("/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewResponse> patchReview(
            @PathVariable Long reviewId,
            @RequestBody Map<String, Object> updates) {
        Long userId = getCurrentUserId();
        log.info("User {} patching review {} with fields: {}", userId, reviewId, updates.keySet());

        ReviewResponse review = reviewService.patchReview(reviewId, updates, userId);
        return ResponseEntity.ok(review);
    }

    /**
     * Upload images for a review
     * POST /api/reviews/{reviewId}/images
     *
     * REQUEST (multipart/form-data):
     * - files: MultipartFile[] (max 5 images, each max 10MB)
     *
     * RESPONSE (200 OK):
     * {
     *   "id": 1,
     *   "productId": 13,
     *   "productName": "Áo thun nam cổ tròn basic",
     *   "userId": 2,
     *   "username": "john_doe",
     *   "rating": 5,
     *   "title": "Sản phẩm tuyệt vời!",
     *   "comment": "Áo rất đẹp...",
     *   "verified": true,
     *   "approved": true,
     *   "images": [
     *     {
     *       "id": 1,
     *       "imageUrl": "https://res.cloudinary.com/.../reviews/review-1/image1.jpg",
     *       "displayOrder": 0
     *     },
     *     {
     *       "id": 2,
     *       "imageUrl": "https://res.cloudinary.com/.../reviews/review-1/image2.jpg",
     *       "displayOrder": 1
     *     }
     *   ],
     *   "helpfulCount": 0,
     *   "reportCount": 0,
     *   "createdAt": "2025-10-25T10:30:00",
     *   "updatedAt": "2025-10-25T10:35:00"
     * }
     */
    @PostMapping(value = "/{reviewId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewResponse> uploadReviewImages(
            @PathVariable Long reviewId,
            @RequestParam("files") MultipartFile[] files) {
        Long userId = getCurrentUserId();
        log.info("User {} uploading {} images for review {}", userId, files.length, reviewId);

        ReviewResponse review = reviewService.uploadReviewImages(reviewId, files, userId);
        return ResponseEntity.ok(review);
    }

    /**
     * Delete a review
     * DELETE /api/reviews/{reviewId}
     *
     * RESPONSE (200 OK):
     * {
     *   "message": "Review deleted successfully",
     *   "reviewId": 1
     * }
     */
    @DeleteMapping("/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> deleteReview(@PathVariable Long reviewId) {
        Long userId = getCurrentUserId();
        log.info("User {} deleting review {}", userId, reviewId);

        reviewService.deleteReview(reviewId, userId);
        return ResponseEntity.ok(Map.of(
                "message", "Review deleted successfully",
                "reviewId", reviewId
        ));
    }

    /**
     * Get all reviews for a product (approved only)
     * GET /api/reviews/product/{productId}?page=0&size=10&sort=createdAt,desc
     *
     * RESPONSE (200 OK):
     * {
     *   "content": [
     *     {
     *       "id": 1,
     *       "productId": 13,
     *       "productName": "Áo thun nam cổ tròn basic",
     *       "userId": 2,
     *       "username": "john_doe",
     *       "userAvatarUrl": "https://...",
     *       "rating": 5,
     *       "title": "Sản phẩm tuyệt vời!",
     *       "comment": "Áo rất đẹp...",
     *       "verified": true,
     *       "approved": true,
     *       "helpfulCount": 12,
     *       "reportCount": 0,
     *       "createdAt": "2025-10-25T10:30:00",
     *       "updatedAt": "2025-10-25T10:30:00"
     *     }
     *   ],
     *   "totalElements": 25,
     *   "totalPages": 3,
     *   "size": 10,
     *   "number": 0
     * }
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<Page<ReviewResponse>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        log.info("Getting reviews for product {}, page {}, size {}", productId, page, size);

        // Parse sort parameter
        String[] sortParams = sort.split(",");
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));

        Page<ReviewResponse> reviews = reviewService.getProductReviews(productId, pageable);
        return ResponseEntity.ok(reviews);
    }

    /**
     * Get product reviews with search and rating filters
     * GET /api/reviews/product/{productId}/filter?keyword=tốt&ratings=5,4&page=0&size=10&sort=createdAt,desc
     *
     * REQUEST PARAMETERS:
     * - keyword (optional): Search keyword in title or comment
     * - ratings (optional): Comma-separated list of ratings (1-5)
     * - page (optional, default=0): Page number
     * - size (optional, default=10): Page size
     * - sort (optional, default=createdAt,desc): Sort field and direction
     *
     * EXAMPLE REQUESTS:
     * 1. Search only: /api/reviews/product/13/filter?keyword=tốt
     * 2. Filter by ratings: /api/reviews/product/13/filter?ratings=5,4
     * 3. Both: /api/reviews/product/13/filter?keyword=tốt&ratings=5,4
     *
     * RESPONSE (200 OK):
     * {
     *   "content": [
     *     {
     *       "id": 1,
     *       "productId": 13,
     *       "productName": "Áo thun nam cổ tròn basic",
     *       "userId": 2,
     *       "username": "john_doe",
     *       "rating": 5,
     *       "title": "Sản phẩm tốt",
     *       "comment": "Rất tốt...",
     *       "verified": true,
     *       "approved": true,
     *       "helpfulCount": 12,
     *       "reportCount": 0,
     *       "createdAt": "2025-10-25T10:30:00",
     *       "updatedAt": "2025-10-25T10:30:00"
     *     }
     *   ],
     *   "totalElements": 5,
     *   "totalPages": 1,
     *   "size": 10,
     *   "number": 0
     * }
     */
    @GetMapping("/product/{productId}/filter")
    public ResponseEntity<Page<ReviewResponse>> getProductReviewsWithFilters(
            @PathVariable Long productId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) java.util.List<Integer> ratings,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        log.info("Getting filtered reviews for product {} - keyword: {}, ratings: {}, page: {}, size: {}",
                productId, keyword, ratings, page, size);

        // Parse sort parameter
        String[] sortParams = sort.split(",");
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));

        Page<ReviewResponse> reviews = reviewService.getProductReviewsWithFilters(productId, keyword, ratings, pageable);
        return ResponseEntity.ok(reviews);
    }

    /**
     * Get my reviews
     * GET /api/reviews/my-reviews?page=0&size=10
     *
     * RESPONSE (200 OK):
     * {
     *   "content": [
     *     {
     *       "id": 1,
     *       "productId": 13,
     *       "productName": "Áo thun nam cổ tròn basic",
     *       "userId": 2,
     *       "username": "john_doe",
     *       "rating": 5,
     *       "title": "Sản phẩm tuyệt vời!",
     *       "comment": "Áo rất đẹp...",
     *       "verified": true,
     *       "approved": true,
     *       "helpfulCount": 12,
     *       "reportCount": 0,
     *       "createdAt": "2025-10-25T10:30:00",
     *       "updatedAt": "2025-10-25T10:30:00"
     *     }
     *   ],
     *   "totalElements": 5,
     *   "totalPages": 1,
     *   "size": 10,
     *   "number": 0
     * }
     */
    @GetMapping("/my-reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ReviewResponse>> getMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long userId = getCurrentUserId();
        log.info("Getting reviews for user {}", userId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReviewResponse> reviews = reviewService.getUserReviews(userId, pageable);
        return ResponseEntity.ok(reviews);
    }

    /**
     * Get a single review by ID
     * GET /api/reviews/{reviewId}
     *
     * RESPONSE (200 OK):
     * {
     *   "id": 1,
     *   "productId": 13,
     *   "productName": "Áo thun nam cổ tròn basic",
     *   "userId": 2,
     *   "username": "john_doe",
     *   "userAvatarUrl": "https://...",
     *   "rating": 5,
     *   "title": "Sản phẩm tuyệt vời!",
     *   "comment": "Áo rất đẹp...",
     *   "verified": true,
     *   "approved": true,
     *   "helpfulCount": 12,
     *   "reportCount": 0,
     *   "createdAt": "2025-10-25T10:30:00",
     *   "updatedAt": "2025-10-25T10:30:00"
     * }
     */
    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> getReview(@PathVariable Long reviewId) {
        log.info("Getting review {}", reviewId);
        ReviewResponse review = reviewService.getReviewById(reviewId);
        return ResponseEntity.ok(review);
    }

    /**
     * Mark a review as helpful
     * POST /api/reviews/{reviewId}/helpful
     *
     * RESPONSE (200 OK):
     * {
     *   "message": "Review marked as helpful",
     *   "reviewId": 1,
     *   "newHelpfulCount": 13
     * }
     */
    @PostMapping("/{reviewId}/helpful")
    public ResponseEntity<Map<String, Object>> markAsHelpful(@PathVariable Long reviewId) {
        log.info("Marking review {} as helpful", reviewId);
        reviewService.markAsHelpful(reviewId);

        ReviewResponse review = reviewService.getReviewById(reviewId);
        return ResponseEntity.ok(Map.of(
                "message", "Review marked as helpful",
                "reviewId", reviewId,
                "newHelpfulCount", review.getHelpfulCount()
        ));
    }

    /**
     * Report a review
     * POST /api/reviews/{reviewId}/report
     *
     * RESPONSE (200 OK):
     * {
     *   "message": "Review reported successfully",
     *   "reviewId": 1
     * }
     */
    @PostMapping("/{reviewId}/report")
    public ResponseEntity<Map<String, Object>> reportReview(@PathVariable Long reviewId) {
        log.info("Reporting review {}", reviewId);
        reviewService.reportReview(reviewId);

        return ResponseEntity.ok(Map.of(
                "message", "Review reported successfully. We will review it soon.",
                "reviewId", reviewId
        ));
    }

    /**
     * Get all approved reviews (public endpoint, with pagination)
     * GET /api/reviews?page=0&size=20&sort=createdAt,desc
     *
     * RESPONSE (200 OK):
     * {
     *   "content": [
     *     {
     *       "id": 1,
     *       "productId": 13,
     *       "productName": "Áo thun nam cổ tròn basic",
     *       "userId": 2,
     *       "username": "john_doe",
     *       "userAvatarUrl": "https://...",
     *       "rating": 5,
     *       "title": "Sản phẩm tuyệt vời!",
     *       "comment": "Áo rất đẹp...",
     *       "verified": true,
     *       "approved": true,
     *       "helpfulCount": 12,
     *       "reportCount": 0,
     *       "createdAt": "2025-10-25T10:30:00",
     *       "updatedAt": "2025-10-25T10:30:00"
     *     }
     *   ],
     *   "totalElements": 100,
     *   "totalPages": 5,
     *   "size": 20,
     *   "number": 0
     * }
     */
    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> getAllApprovedReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        log.info("Getting all approved reviews, page {}, size {}", page, size);

        // Parse sort parameter
        String[] sortParams = sort.split(",");
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));

        Page<ReviewResponse> reviews = reviewService.getAllApprovedReviews(pageable);
        return ResponseEntity.ok(reviews);
    }

    /**
     * Get product rating statistics
     * GET /api/reviews/product/{productId}/stats
     *
     * RESPONSE (200 OK):
     * {
     *   "productId": 13,
     *   "productName": "Áo thun nam cổ tròn basic",
     *   "averageRating": 4.5,
     *   "totalReviews": 25,
     *   "ratingDistribution": {
     *     "1": 1,
     *     "2": 2,
     *     "3": 3,
     *     "4": 8,
     *     "5": 11
     *   },
     *   "ratingPercentages": {
     *     "1": 4.0,
     *     "2": 8.0,
     *     "3": 12.0,
     *     "4": 32.0,
     *     "5": 44.0
     *   }
     * }
     */
    @GetMapping("/product/{productId}/stats")
    public ResponseEntity<Object> getProductRatingStats(@PathVariable Long productId) {
        log.info("Getting rating statistics for product {}", productId);
        Object stats = reviewService.getProductRatingStats(productId);
        return ResponseEntity.ok(stats);
    }

    // ==================== ADMIN ENDPOINTS ====================

    /**
     * Admin: Get all reviews (including unapproved)
     * GET /api/admin/reviews?page=0&size=20
     *
     * RESPONSE (200 OK):
     * {
     *   "content": [
     *     {
     *       "id": 1,
     *       "productId": 13,
     *       "productName": "Áo thun nam cổ tròn basic",
     *       "userId": 2,
     *       "username": "john_doe",
     *       "rating": 5,
     *       "title": "Sản phẩm tuyệt vời!",
     *       "comment": "Áo rất đẹp...",
     *       "verified": true,
     *       "approved": false,
     *       "helpfulCount": 0,
     *       "reportCount": 0,
     *       "createdAt": "2025-10-25T10:30:00",
     *       "updatedAt": "2025-10-25T10:30:00"
     *     }
     *   ],
     *   "totalElements": 50,
     *   "totalPages": 3,
     *   "size": 20,
     *   "number": 0
     * }
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<Page<ReviewResponse>> getAllReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Admin: Getting all reviews, page {}, size {}", page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReviewResponse> reviews = reviewService.getAllReviews(pageable);
        return ResponseEntity.ok(reviews);
    }

    /**
     * Admin: Approve a review
     * PATCH /api/admin/reviews/{reviewId}/approve
     *
     * RESPONSE (200 OK):
     * {
     *   "id": 1,
     *   "productId": 13,
     *   "productName": "Áo thun nam cổ tròn basic",
     *   "userId": 2,
     *   "username": "john_doe",
     *   "rating": 5,
     *   "title": "Sản phẩm tuyệt vời!",
     *   "comment": "Áo rất đẹp...",
     *   "verified": true,
     *   "approved": true,
     *   "helpfulCount": 0,
     *   "reportCount": 0,
     *   "createdAt": "2025-10-25T10:30:00",
     *   "updatedAt": "2025-10-25T10:30:00"
     * }
     */
    @PatchMapping("/admin/{reviewId}/approve")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<ReviewResponse> approveReview(@PathVariable Long reviewId) {
        log.info("Admin: Approving review {}", reviewId);
        ReviewResponse review = reviewService.approveReview(reviewId);
        return ResponseEntity.ok(review);
    }

    /**
     * Admin: Reject a review
     * PATCH /api/admin/reviews/{reviewId}/reject
     *
     * RESPONSE (200 OK):
     * {
     *   "id": 1,
     *   "productId": 13,
     *   "productName": "Áo thun nam cổ tròn basic",
     *   "userId": 2,
     *   "username": "john_doe",
     *   "rating": 5,
     *   "title": "Sản phẩm tuyệt vời!",
     *   "comment": "Áo rất đẹp...",
     *   "verified": true,
     *   "approved": false,
     *   "helpfulCount": 0,
     *   "reportCount": 0,
     *   "createdAt": "2025-10-25T10:30:00",
     *   "updatedAt": "2025-10-25T10:30:00"
     * }
     */
    @PatchMapping("/admin/{reviewId}/reject")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<ReviewResponse> rejectReview(@PathVariable Long reviewId) {
        log.info("Admin: Rejecting review {}", reviewId);
        ReviewResponse review = reviewService.rejectReview(reviewId);
        return ResponseEntity.ok(review);
    }

    /**
     * Admin: Delete a review
     * DELETE /api/admin/reviews/{reviewId}
     *
     * RESPONSE (200 OK):
     * {
     *   "message": "Review deleted successfully by admin",
     *   "reviewId": 1
     * }
     */
    @DeleteMapping("/admin/{reviewId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> adminDeleteReview(@PathVariable Long reviewId) {
        log.info("Admin: Deleting review {}", reviewId);
        reviewService.adminDeleteReview(reviewId);

        return ResponseEntity.ok(Map.of(
                "message", "Review deleted successfully by admin",
                "reviewId", reviewId
        ));
    }

    /**
     * Helper method to get current authenticated user ID from JWT token
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserPrincipal) {
            UserPrincipal userPrincipal = (UserPrincipal) principal;
            return userPrincipal.getId();
        }

        throw new IllegalStateException("Cannot determine user ID from authentication principal");
    }
}
