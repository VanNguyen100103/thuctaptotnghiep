package com.ut.edu.backend.review;

import com.ut.edu.backend.product.Product;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new review
 *
 * Example JSON:
 * {
 *   "productId": 13,
 *   "rating": 5,
 *   "title": "Sản phẩm tuyệt vời!",
 *   "comment": "Áo rất đẹp, chất liệu cotton mát mẻ, form chuẩn. Tôi rất hài lòng với sản phẩm này."
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateReviewRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @NotBlank(message = "Review title is required")
    @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
    private String title;

    @NotBlank(message = "Review comment is required")
    @Size(min = 10, max = 2000, message = "Comment must be between 10 and 2000 characters")
    private String comment;
}
