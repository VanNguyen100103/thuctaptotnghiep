package com.ut.edu.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * ProductImage entity for storing multiple images per product
 * Images are stored in Cloudinary with folder-specific configuration
 */
@Entity
@Table(name = "product_images", indexes = {
    @Index(name = "idx_image_product", columnList = "product_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = "product")
public class ProductImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @NotBlank(message = "Image URL is required")
    @Column(nullable = false, length = 500)
    private String imageUrl;

    @NotBlank(message = "Cloudinary public ID is required")
    @Column(nullable = false, unique = true)
    private String cloudinaryPublicId;

    @Column(length = 200)
    private String altText;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    // Cloudinary folder path (e.g., "products/men/shirts/product-123")
    @Column(length = 300)
    private String folderPath;

    // Thumbnail URL for performance
    @Column(length = 500)
    private String thumbnailUrl;

    // Color variant for this image (e.g., "Đen", "Xanh Navy", "Xám")
    // Allows filtering images by selected color
    @Column(length = 50)
    private String color;
}
