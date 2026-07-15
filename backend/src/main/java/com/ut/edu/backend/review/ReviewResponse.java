package com.ut.edu.backend.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Response DTO for Review
 *
 * Example JSON:
 * {
 *   "id": 1,
 *   "productId": 13,
 *   "productName": "Áo thun nam cổ tròn basic",
 *   "userId": 2,
 *   "username": "john_doe",
 *   "rating": 5,
 *   "title": "Sản phẩm tuyệt vời!",
 *   "comment": "Áo rất đẹp, chất liệu cotton mát mẻ, form chuẩn. Tôi rất hài lòng với sản phẩm này.",
 *   "verified": true,
 *   "approved": true,
 *   "helpfulCount": 12,
 *   "reportCount": 0,
 *   "createdAt": "2025-10-25T10:30:00",
 *   "updatedAt": "2025-10-25T10:30:00",
 *   "images": [
 *     {
 *       "id": 1,
 *       "imageUrl": "https://res.cloudinary.com/.../reviews/review-1/image1.jpg",
 *       "displayOrder": 0
 *     }
 *   ]
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponse {

    private Long id;
    private Long productId;
    private String productName;
    private Long userId;
    private String username;
    private String userAvatarUrl;
    private Integer rating;
    private String title;
    private String comment;
    private Boolean verified;
    private Boolean approved;
    private Integer helpfulCount;
    private Integer reportCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ReviewImageDto> images;

    /**
     * Convert Review entity to ReviewResponse DTO
     */
    public static ReviewResponse fromEntity(Review review) {
        // Convert images to DTOs
        List<ReviewImageDto> imageDtos = review.getImages() != null ?
                review.getImages().stream()
                        .map(ReviewImageDto::fromEntity)
                        .collect(Collectors.toList()) :
                new ArrayList<>();

        // Safely get user info (handle potential null)
        Long userId = review.getUser() != null ? review.getUser().getId() : null;
        String username = review.getUser() != null ? review.getUser().getUsername() : "Anonymous";
        String userAvatarUrl = review.getUser() != null ? review.getUser().getAvatarUrl() : null;

        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .productName(review.getProduct().getName())
                .userId(userId)
                .username(username)
                .userAvatarUrl(userAvatarUrl)
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .verified(review.getVerified())
                .approved(review.getApproved())
                .helpfulCount(review.getHelpfulCount())
                .reportCount(review.getReportCount())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .images(imageDtos)
                .build();
    }

    /**
     * Nested DTO for ReviewImage
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReviewImageDto {
        private Long id;
        private String imageUrl;
        private Integer displayOrder;

        public static ReviewImageDto fromEntity(ReviewImage image) {
            return ReviewImageDto.builder()
                    .id(image.getId())
                    .imageUrl(image.getImageUrl())
                    .displayOrder(image.getDisplayOrder())
                    .build();
        }
    }
}
