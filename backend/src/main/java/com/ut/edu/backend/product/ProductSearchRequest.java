package com.ut.edu.backend.product;

import com.ut.edu.backend.category.Category;

import com.ut.edu.backend.validation.NoSQLInjection;
import com.ut.edu.backend.validation.SafeText;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Product Search Request DTO
 * Demonstrates usage of custom security validators
 */
@Data
public class ProductSearchRequest {

    /**
     * Search keyword - protected against XSS and SQL injection
     */
    @SafeText(message = "Search keyword contains dangerous content")
    @NoSQLInjection(message = "Search keyword contains SQL injection patterns")
    @Size(max = 200, message = "Search keyword too long")
    private String keyword;

    /**
     * Category filter - protected against SQL injection
     */
    @NoSQLInjection
    private String category;

    /**
     * Brand filter - protected against SQL injection
     */
    @NoSQLInjection
    @Size(max = 100, message = "Brand name too long")
    private String brand;

    /**
     * Minimum price
     */
    @Min(value = 0, message = "Price cannot be negative")
    private Double minPrice;

    /**
     * Maximum price
     */
    @Min(value = 0, message = "Price cannot be negative")
    private Double maxPrice;

    /**
     * Sort field - restricted to prevent SQL injection
     */
    @NoSQLInjection
    private String sortBy;

    /**
     * Sort direction (asc/desc)
     */
    @NoSQLInjection
    @Size(max = 4, message = "Invalid sort direction")
    private String sortDirection;

    /**
     * Page number
     */
    @Min(value = 0, message = "Page number cannot be negative")
    private Integer page = 0;

    /**
     * Page size
     */
    @Min(value = 1, message = "Page size must be at least 1")
    private Integer size = 20;
}
