package com.ut.edu.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Product entity for fashion items
 */
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_name", columnList = "name"),
    @Index(name = "idx_product_slug", columnList = "slug"),
    @Index(name = "idx_product_sku", columnList = "sku"),
    @Index(name = "idx_product_price", columnList = "price"),
    @Index(name = "idx_product_active", columnList = "active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"categories", "images", "reviews", "cartItems", "orderItems"})
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Product name is required")
    @Size(min = 3, max = 200, message = "Product name must be between 3 and 200 characters")
    @Column(nullable = false, length = 200)
    private String name;

    @NotBlank(message = "Product slug is required")
    @Column(unique = true, nullable = false, length = 200)
    private String slug;

    @NotBlank(message = "SKU is required")
    @Column(unique = true, nullable = false, length = 100)
    private String sku;

    @Column(length = 2000)
    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Compare at price must be greater than or equal to 0")
    @Column(precision = 10, scale = 2)
    private BigDecimal compareAtPrice; // Original price for showing discounts

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Column(nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean featured = false;

    // Fashion-specific attributes
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_sizes", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "size")
    @Builder.Default
    private Set<String> availableSizes = new HashSet<>(); // S, M, L, XL, 2XL, 3XL

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_colors", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "color")
    @Builder.Default
    private Set<String> availableColors = new HashSet<>();

    @Column(length = 255)
    private String brand;

    @Column(length = 500)
    private String material; // Cotton, Polyester, etc.

    @Column(length = 50)
    private String gender; // Men, Women, Unisex

    // Product metrics
    @Column(nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @Column(nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer soldCount = 0;

    // SEO fields
    @Column(length = 200)
    private String metaTitle;

    @Column(length = 500)
    private String metaDescription;

    @Column(length = 200)
    private String metaKeywords;

    // Relationships
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "product_categories",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Builder.Default
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private Set<ProductImage> images = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @JsonIgnore
    @Builder.Default
    private Set<Review> reviews = new HashSet<>();

    @OneToMany(mappedBy = "product")
    @JsonIgnore
    @Builder.Default
    private Set<CartItem> cartItems = new HashSet<>();

    @OneToMany(mappedBy = "product")
    @JsonIgnore
    @Builder.Default
    private Set<OrderItem> orderItems = new HashSet<>();

    // Helper methods
    public void addCategory(Category category) {
        categories.add(category);
        category.getProducts().add(this);
    }

    public void removeCategory(Category category) {
        categories.remove(category);
        category.getProducts().remove(this);
    }

    public void addImage(ProductImage image) {
        images.add(image);
        image.setProduct(this);
    }

    public void removeImage(ProductImage image) {
        images.remove(image);
        image.setProduct(null);
    }

    public void addReview(Review review) {
        reviews.add(review);
        review.setProduct(this);
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementSoldCount(Integer quantity) {
        this.soldCount += quantity;
    }

    public void decrementStock(Integer quantity) {
        if (this.stockQuantity >= quantity) {
            this.stockQuantity -= quantity;
        } else {
            throw new IllegalArgumentException("Insufficient stock");
        }
    }

    public void incrementStock(Integer quantity) {
        this.stockQuantity += quantity;
    }

    public boolean isInStock() {
        return this.stockQuantity > 0;
    }

    public boolean hasDiscount() {
        return compareAtPrice != null && compareAtPrice.compareTo(price) > 0;
    }

    public BigDecimal getDiscountPercentage() {
        if (hasDiscount()) {
            BigDecimal discount = compareAtPrice.subtract(price);
            return discount.divide(compareAtPrice, 2, BigDecimal.ROUND_HALF_UP)
                          .multiply(new BigDecimal("100"));
        }
        return BigDecimal.ZERO;
    }
}
