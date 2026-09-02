package com.ut.edu.backend.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for AdminProductController#createProductVariants - generates
 * one Product row per (color, size) combination, all sharing one
 * variantGroupId. Unlike createProduct/updateProduct (raw Product entity
 * binding), this payload shape doesn't map onto one flat entity, so it gets
 * a real DTO + @Valid instead.
 */
@Data
public class CreateProductVariantsRequest {

    @NotBlank(message = "Product name is required")
    @Size(min = 3, max = 160, message = "Product name must be between 3 and 160 characters")
    private String name;

    private String shortDescription;
    private String description;
    private List<Long> categoryIds;
    private String brand;
    private String material;
    private String gender;

    @DecimalMin(value = "0.0", message = "Compare at price must be greater than or equal to 0")
    private BigDecimal compareAtPrice;

    @DecimalMin(value = "0.0", message = "Tax rate must be greater than or equal to 0")
    @DecimalMax(value = "100.0", message = "Tax rate must be less than or equal to 100")
    private BigDecimal taxRate;

    @Min(value = 0, message = "Minimum stock threshold cannot be negative")
    private Integer minStockThreshold;

    @Min(value = 0, message = "Maximum stock threshold cannot be negative")
    private Integer maxStockThreshold;

    private Boolean active;
    private Boolean featured;

    @NotEmpty(message = "At least one variant row is required")
    @Valid
    private List<VariantRow> variants;

    @Data
    public static class VariantRow {
        @NotBlank(message = "Color is required")
        private String color;

        @NotBlank(message = "Size is required")
        private String size;

        @NotBlank(message = "SKU is required")
        @Size(max = 100, message = "SKU must be at most 100 characters")
        private String sku;

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
        private BigDecimal price;

        @DecimalMin(value = "0.0", message = "Cost price must be greater than or equal to 0")
        private BigDecimal costPrice;

        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock quantity cannot be negative")
        private Integer stockQuantity;
    }
}
