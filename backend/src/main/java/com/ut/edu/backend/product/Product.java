package com.ut.edu.backend.product;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.cart.CartItem;
import com.ut.edu.backend.order.OrderItem;
import com.ut.edu.backend.review.Review;
import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    @Index(name = "idx_product_active", columnList = "active"),
    @Index(name = "idx_products_store", columnList = "store_id"),
    @Index(name = "idx_products_variant_group", columnList = "variant_group_id")
}, uniqueConstraints = {
    // slug/sku are unique per store, not globally (multi-tenant)
    @UniqueConstraint(name = "uk_products_store_slug", columnNames = {"store_id", "slug"}),
    @UniqueConstraint(name = "uk_products_store_sku", columnNames = {"store_id", "sku"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "categories", "images", "reviews", "cartItems", "orderItems"})
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant link: the store this product belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    @NotBlank(message = "Product name is required")
    @Size(min = 3, max = 200, message = "Product name must be between 3 and 200 characters")
    @Column(nullable = false, length = 200)
    private String name;

    @NotBlank(message = "Product slug is required")
    @Column(nullable = false, length = 200)
    private String slug;

    @NotBlank(message = "SKU is required")
    @Column(nullable = false, length = 100)
    private String sku;

    /**
     * Opaque grouping key (UUID string) shared by all Color x Size sibling
     * rows generated together via AdminProductController#createProductVariants.
     * Null for ordinary, non-variant products. Each sibling remains a
     * complete, independently trackable Product - this column exists only
     * so the UI can group and label them together.
     */
    @Column(name = "variant_group_id", length = 36)
    private String variantGroupId;

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

    @DecimalMin(value = "0.0", message = "Cost price must be greater than or equal to 0")
    @Column(precision = 10, scale = 2)
    private BigDecimal costPrice; // Wholesale/purchase cost - store-internal only, never exposed on the public storefront

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Column(nullable = false)
    private Integer stockQuantity;

    @Min(value = 0, message = "Minimum stock threshold cannot be negative")
    private Integer minStockThreshold; // Restock alert floor - optional, no default (unset = no per-product alert)

    @Min(value = 0, message = "Maximum stock threshold cannot be negative")
    private Integer maxStockThreshold; // Overstock ceiling - optional, no default

    @DecimalMin(value = "0.0", message = "Tax rate must be greater than or equal to 0")
    @DecimalMax(value = "100.0", message = "Tax rate must be less than or equal to 100")
    @Column(precision = 5, scale = 2)
    private BigDecimal taxRate; // VAT % - OWNER-only to set (see AdminProductController), store-internal, never exposed on the public storefront

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean featured = false;

    // Decorative size/color tags on a simple (non-variant) product - fashion-shaped
    // by convention, but optional and unused by other industries. Not used by
    // Color x Size variant generation any more (see `attributes` below).
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

    /**
     * Industry-agnostic attribute values (e.g. {"Kích cỡ":"M","Màu sắc":"Đen"}
     * for fashion, {"Hương vị":"Dâu"} for F&B) - the canonical store of "what
     * makes this specific variant row different" for products generated via
     * AdminProductController#createProductVariants. Free-named, up to 3 axes
     * per store's choice; not tied to any fixed size/color/flavor vocabulary.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_attributes", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "attribute_name")
    @Column(name = "attribute_value")
    @Builder.Default
    private Map<String, String> attributes = new HashMap<>();

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
