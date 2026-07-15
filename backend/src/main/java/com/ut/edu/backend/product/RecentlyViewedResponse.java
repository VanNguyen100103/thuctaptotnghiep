package com.ut.edu.backend.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Response DTO for recently viewed products
 *
 * Example JSON:
 * {
 *   "id": 13,
 *   "name": "Áo thun nam cổ tròn basic",
 *   "slug": "ao-thun-nam-co-tron-basic",
 *   "price": 199000,
 *   "compareAtPrice": 299000,
 *   "discountPercentage": 33.44,
 *   "imageUrl": "https://res.cloudinary.com/.../products/product-13/main.jpg",
 *   "averageRating": 4.5,
 *   "reviewCount": 24,
 *   "inStock": true,
 *   "viewedAt": "2025-11-02T14:30:00"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentlyViewedResponse {

    private Long id;
    private String name;
    private String slug;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private BigDecimal discountPercentage;
    private String imageUrl;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Boolean inStock;
    private LocalDateTime viewedAt;
    private String brand;
    private List<String> availableColors;
    private List<String> availableSizes;

    /**
     * Convert Product entity to RecentlyViewedResponse DTO
     */
    public static RecentlyViewedResponse fromProduct(Product product) {
        return fromProduct(product, null);
    }

    /**
     * Convert Product entity to RecentlyViewedResponse DTO with viewedAt timestamp
     */
    public static RecentlyViewedResponse fromProduct(Product product, LocalDateTime viewedAt) {
        // Get the first product image (main image)
        String imageUrl = null;
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            imageUrl = product.getImages().stream()
                    .filter(img -> img.getDisplayOrder() != null)
                    .sorted((a, b) -> a.getDisplayOrder().compareTo(b.getDisplayOrder()))
                    .findFirst()
                    .map(ProductImage::getImageUrl)
                    .orElse(null);
        }

        return RecentlyViewedResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .price(product.getPrice())
                .compareAtPrice(product.getCompareAtPrice())
                .discountPercentage(product.getDiscountPercentage())
                .imageUrl(imageUrl)
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .inStock(product.isInStock())
                .viewedAt(viewedAt)
                .brand(product.getBrand())
                .availableColors(product.getAvailableColors() != null ?
                    product.getAvailableColors().stream().collect(Collectors.toList()) : null)
                .availableSizes(product.getAvailableSizes() != null ?
                    product.getAvailableSizes().stream().collect(Collectors.toList()) : null)
                .build();
    }
}
