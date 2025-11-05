package com.ut.edu.backend.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing review
 *
 * Example JSON:
 * {
 *   "rating": 4,
 *   "title": "Sản phẩm tốt nhưng hơi nhỏ",
 *   "comment": "Sau khi mặc thử thì thấy chất liệu tốt nhưng size hơi nhỏ. Nên mua size lớn hơn 1 size."
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateReviewRequest {

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
