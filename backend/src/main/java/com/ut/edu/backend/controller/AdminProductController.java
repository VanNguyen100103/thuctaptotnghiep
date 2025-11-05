package com.ut.edu.backend.controller;

import com.ut.edu.backend.model.Category;
import com.ut.edu.backend.model.Product;
import com.ut.edu.backend.repository.CategoryRepository;
import com.ut.edu.backend.repository.ProductRepository;
import com.ut.edu.backend.util.HtmlEntityDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin Product Controller
 * Product management for administrators
 */
@RestController
@RequestMapping("/admin/products")
@PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
@Slf4j
public class AdminProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    /**
     * Get all products with pagination
     * GET /api/admin/products?page=0&size=20&active=true
     */
    @GetMapping
    public ResponseEntity<?> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) Boolean active) {
        try {
            Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);

            Page<Product> products;
            if (active != null && active) {
                products = productRepository.findByActiveTrue(pageable);
            } else if (active != null && !active) {
                // Find inactive products - filter in memory if method not available
                List<Product> allProducts = productRepository.findAll();
                List<Product> inactiveProducts = allProducts.stream()
                        .filter(p -> !p.getActive())
                        .skip((long) page * size)
                        .limit(size)
                        .collect(java.util.stream.Collectors.toList());

                products = new org.springframework.data.domain.PageImpl<>(
                        inactiveProducts,
                        pageable,
                        allProducts.stream().filter(p -> !p.getActive()).count()
                );
            } else {
                products = productRepository.findAll(pageable);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("products", products.getContent());
            response.put("currentPage", products.getNumber());
            response.put("totalItems", products.getTotalElements());
            response.put("totalPages", products.getTotalPages());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve products"));
        }
    }

    /**
     * Search products (admin - includes inactive products)
     * GET /api/admin/products/search?query=shirt&page=0&size=20
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchProducts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Search query is required"));
            }

            // Decode HTML entities if present (e.g., &Aacute; -> Á)
            String decodedQuery = HtmlEntityDecoder.decode(query.trim());
            log.debug("Search query: original='{}', decoded='{}'", query, decodedQuery);

            Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);

            // Use optimized repository query (searches name, description, brand, SKU)
            Page<Product> products = productRepository.adminSearchProducts(decodedQuery, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("products", products.getContent());
            response.put("currentPage", products.getNumber());
            response.put("totalItems", products.getTotalElements());
            response.put("totalPages", products.getTotalPages());
            response.put("query", decodedQuery);  // Return decoded query

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to search products with query: {}", query, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to search products"));
        }
    }

    /**
     * Get product by ID
     * GET /api/admin/products/{productId}
     */
    @GetMapping("/{productId}")
    public ResponseEntity<?> getProductById(@PathVariable Long productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            return ResponseEntity.ok(product);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get product: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve product"));
        }
    }

    /**
     * Create new product
     * POST /api/admin/products
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createProduct(@RequestBody Product product) {
        try {
            // Validate required fields
            if (product.getName() == null || product.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product name is required"));
            }

            if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Valid price is required"));
            }

            // Set default values
            if (product.getActive() == null) {
                product.setActive(true);
            }
            if (product.getStockQuantity() == null) {
                product.setStockQuantity(0);
            }

            Product savedProduct = productRepository.save(product);

            log.info("New product created: {} (ID: {})", savedProduct.getName(), savedProduct.getId());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Product created successfully",
                            "product", savedProduct
                    ));

        } catch (Exception e) {
            log.error("Failed to create product", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create product"));
        }
    }

    /**
     * Update product
     * PUT /api/admin/products/{productId}
     */
    @PutMapping("/{productId}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long productId,
            @RequestBody Product productUpdates) {
        try {
            Product existingProduct = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            // Update fields
            if (productUpdates.getName() != null) {
                existingProduct.setName(productUpdates.getName());
            }
            if (productUpdates.getDescription() != null) {
                existingProduct.setDescription(productUpdates.getDescription());
            }
            if (productUpdates.getPrice() != null) {
                existingProduct.setPrice(productUpdates.getPrice());
            }
            if (productUpdates.getStockQuantity() != null) {
                existingProduct.setStockQuantity(productUpdates.getStockQuantity());
            }
            if (productUpdates.getBrand() != null) {
                existingProduct.setBrand(productUpdates.getBrand());
            }
            if (productUpdates.getMaterial() != null) {
                existingProduct.setMaterial(productUpdates.getMaterial());
            }
            if (productUpdates.getGender() != null) {
                existingProduct.setGender(productUpdates.getGender());
            }
            if (productUpdates.getCompareAtPrice() != null) {
                existingProduct.setCompareAtPrice(productUpdates.getCompareAtPrice());
            }
            // Update available sizes and colors
            if (productUpdates.getAvailableSizes() != null) {
                existingProduct.setAvailableSizes(productUpdates.getAvailableSizes());
            }
            if (productUpdates.getAvailableColors() != null) {
                existingProduct.setAvailableColors(productUpdates.getAvailableColors());
            }
            if (productUpdates.getCategories() != null) {
                existingProduct.setCategories(productUpdates.getCategories());
            }

            Product savedProduct = productRepository.save(existingProduct);

            log.info("Product updated: {} (ID: {})", savedProduct.getName(), savedProduct.getId());

            return ResponseEntity.ok(Map.of(
                    "message", "Product updated successfully",
                    "product", savedProduct
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update product: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update product"));
        }
    }

    /**
     * Update product stock
     * PATCH /api/admin/products/{productId}/stock
     */
    @PatchMapping("/{productId}/stock")
    public ResponseEntity<?> updateProductStock(
            @PathVariable Long productId,
            @RequestBody Map<String, Integer> request) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            Integer stockQuantity = request.get("stockQuantity");
            if (stockQuantity == null || stockQuantity < 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Valid stock quantity is required"));
            }

            product.setStockQuantity(stockQuantity);
            productRepository.save(product);

            log.info("Product {} stock updated to: {}", productId, stockQuantity);

            return ResponseEntity.ok(Map.of(
                    "message", "Stock updated successfully",
                    "productId", productId,
                    "stockQuantity", stockQuantity
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update product stock: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update stock"));
        }
    }

    /**
     * Activate/Deactivate product
     * PATCH /api/admin/products/{productId}/status
     */
    @PatchMapping("/{productId}/status")
    public ResponseEntity<?> updateProductStatus(
            @PathVariable Long productId,
            @RequestBody Map<String, Boolean> request) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            Boolean active = request.get("active");
            if (active == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "active field is required"));
            }

            product.setActive(active);
            productRepository.save(product);

            String status = active ? "activated" : "deactivated";
            log.info("Product {} {}", productId, status);

            return ResponseEntity.ok(Map.of(
                    "message", "Product " + status + " successfully",
                    "productId", productId,
                    "active", active
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update product status: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update product status"));
        }
    }

    /**
     * Delete product (soft delete - set active to false)
     * DELETE /api/admin/products/{productId}
     */
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteProduct(@PathVariable Long productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            // Soft delete
            product.setActive(false);
            productRepository.save(product);

            log.warn("Product {} deleted (deactivated) by admin", productId);

            return ResponseEntity.ok(Map.of(
                    "message", "Product deleted successfully",
                    "productId", productId
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to delete product: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete product"));
        }
    }

    /**
     * Get product statistics
     * GET /api/admin/products/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getProductStatistics() {
        try {
            long totalProducts = productRepository.count();

            // Count active and inactive products
            long activeProducts = productRepository.findAll().stream()
                .filter(Product::getActive)
                .count();
            long inactiveProducts = totalProducts - activeProducts;

            // Count out of stock products
            long outOfStock = productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() == 0)
                .count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalProducts", totalProducts);
            stats.put("activeProducts", activeProducts);
            stats.put("inactiveProducts", inactiveProducts);
            stats.put("outOfStock", outOfStock);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to get product statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve statistics"));
        }
    }

    /**
     * Bulk update product prices (e.g., apply discount)
     * POST /api/admin/products/bulk-price-update
     */
    @PostMapping("/bulk-price-update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> bulkPriceUpdate(@RequestBody Map<String, Object> request) {
        try {
            // Validate and parse input
            @SuppressWarnings("unchecked")
            List<?> rawProductIds = (List<?>) request.get("productIds");
            
            // Convert to List<Long> (handles both Integer and Long from JSON)
            List<Long> productIds = new ArrayList<>();
            if (rawProductIds != null) {
                for (Object id : rawProductIds) {
                    if (id instanceof Integer) {
                        productIds.add(((Integer) id).longValue());
                    } else if (id instanceof Long) {
                        productIds.add((Long) id);
                    } else if (id != null) {
                        productIds.add(Long.parseLong(id.toString()));
                    }
                }
            }
            
            String action = (String) request.get("action");
            
            // Safe number conversion
            Object percentageObj = request.get("percentage");
            BigDecimal percentage;
            if (percentageObj instanceof Integer) {
                percentage = new BigDecimal((Integer) percentageObj);
            } else if (percentageObj instanceof Double) {
                percentage = BigDecimal.valueOf((Double) percentageObj);
            } else if (percentageObj != null) {
                percentage = new BigDecimal(percentageObj.toString());
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "percentage is required"));
            }

            // Validation
            if (productIds == null || productIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product IDs are required"));
            }
            
            if (!"increase".equals(action) && !"decrease".equals(action)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "action must be 'increase' or 'decrease'"));
            }
            
            if (percentage.compareTo(BigDecimal.ZERO) <= 0 || percentage.compareTo(new BigDecimal("100")) > 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "percentage must be between 0 and 100"));
            }

            int updatedCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (Long productId : productIds) {
                try {
                    Product product = productRepository.findById(productId).orElse(null);
                    
                    if (product == null) {
                        errors.add("Product " + productId + " not found");
                        continue;
                    }
                    
                    BigDecimal currentPrice = product.getPrice();
                    if (currentPrice == null) {
                        errors.add("Product " + productId + " has null price");
                        continue;
                    }
                    
                    // FIX: Correct BigDecimal division with proper scale
                    BigDecimal multiplier = percentage.divide(
                        new BigDecimal("100"), 
                        4,  // 4 decimal places for precision
                        RoundingMode.HALF_UP
                    );
                    
                    BigDecimal change = currentPrice.multiply(multiplier);

                    BigDecimal newPrice;
                    if ("increase".equals(action)) {
                        newPrice = currentPrice.add(change);
                    } else {
                        newPrice = currentPrice.subtract(change);
                    }
                    
                    // Round to 2 decimal places for currency
                    newPrice = newPrice.setScale(2, RoundingMode.HALF_UP);
                    
                    // Prevent negative price
                    if (newPrice.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Product " + productId + " would have negative price");
                        continue;
                    }

                    product.setPrice(newPrice);
                    productRepository.save(product);
                    updatedCount++;
                    
                    log.info("Product {} price updated: {} -> {} ({}% {})", 
                        productId, currentPrice, newPrice, percentage, action);
                    
                } catch (Exception e) {
                    errors.add("Product " + productId + ": " + e.getMessage());
                    log.error("Error updating product {}", productId, e);
                }
            }

            log.info("Bulk price update completed: {} products updated, {} errors", 
                updatedCount, errors.size());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Bulk update completed");
            response.put("updatedCount", updatedCount);
            response.put("totalRequested", productIds.size());
            
            if (!errors.isEmpty()) {
                response.put("errors", errors);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to bulk update prices", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to update prices",
                        "details", e.getMessage()
                    ));
        }
    }

    /**
     * Update product categories
     * PATCH /api/admin/products/{productId}/categories
     *
     * Request body:
     * {
     *   "categoryIds": [1, 2, 3]
     * }
     */
    @PatchMapping("/{productId}/categories")
    public ResponseEntity<?> updateProductCategories(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        try {
            // Find product
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            // Extract category IDs from request
            @SuppressWarnings("unchecked")
            List<?> rawCategoryIds = (List<?>) request.get("categoryIds");

            if (rawCategoryIds == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "categoryIds field is required"));
            }

            // Convert to List<Long>
            Set<Long> categoryIds = new HashSet<>();
            for (Object id : rawCategoryIds) {
                if (id instanceof Integer) {
                    categoryIds.add(((Integer) id).longValue());
                } else if (id instanceof Long) {
                    categoryIds.add((Long) id);
                } else if (id != null) {
                    categoryIds.add(Long.parseLong(id.toString()));
                }
            }

            // Fetch categories from database
            List<Category> categories = categoryRepository.findAllById(categoryIds);

            // Check if all categories exist
            if (categories.size() != categoryIds.size()) {
                Set<Long> foundIds = categories.stream()
                        .map(Category::getId)
                        .collect(Collectors.toSet());

                Set<Long> missingIds = new HashSet<>(categoryIds);
                missingIds.removeAll(foundIds);

                return ResponseEntity.badRequest()
                        .body(Map.of(
                            "error", "Some categories not found",
                            "missingIds", missingIds
                        ));
            }

            // Update product categories
            product.setCategories(new HashSet<>(categories));
            Product savedProduct = productRepository.save(product);

            log.info("Product {} categories updated: {} categories assigned",
                    productId, categories.size());

            return ResponseEntity.ok(Map.of(
                    "message", "Product categories updated successfully",
                    "productId", productId,
                    "categories", savedProduct.getCategories().stream()
                            .map(c -> Map.of(
                                "id", c.getId(),
                                "name", c.getName(),
                                "slug", c.getSlug()
                            ))
                            .collect(Collectors.toList())
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update product categories: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to update product categories",
                        "details", e.getMessage()
                    ));
        }
    }

    /**
     * Add categories to product (append, don't replace)
     * POST /api/admin/products/{productId}/categories
     *
     * Request body:
     * {
     *   "categoryIds": [1, 2]
     * }
     */
    @PostMapping("/{productId}/categories")
    public ResponseEntity<?> addProductCategories(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        try {
            // Find product
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            // Extract category IDs from request
            @SuppressWarnings("unchecked")
            List<?> rawCategoryIds = (List<?>) request.get("categoryIds");

            if (rawCategoryIds == null || rawCategoryIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "categoryIds field is required and must not be empty"));
            }

            // Convert to List<Long>
            Set<Long> categoryIds = new HashSet<>();
            for (Object id : rawCategoryIds) {
                if (id instanceof Integer) {
                    categoryIds.add(((Integer) id).longValue());
                } else if (id instanceof Long) {
                    categoryIds.add((Long) id);
                } else if (id != null) {
                    categoryIds.add(Long.parseLong(id.toString()));
                }
            }

            // Fetch categories from database
            List<Category> newCategories = categoryRepository.findAllById(categoryIds);

            // Check if all categories exist
            if (newCategories.size() != categoryIds.size()) {
                Set<Long> foundIds = newCategories.stream()
                        .map(Category::getId)
                        .collect(Collectors.toSet());

                Set<Long> missingIds = new HashSet<>(categoryIds);
                missingIds.removeAll(foundIds);

                return ResponseEntity.badRequest()
                        .body(Map.of(
                            "error", "Some categories not found",
                            "missingIds", missingIds
                        ));
            }

            // Add new categories to existing ones
            Set<Category> existingCategories = product.getCategories();
            if (existingCategories == null) {
                existingCategories = new HashSet<>();
            }

            int beforeCount = existingCategories.size();
            existingCategories.addAll(newCategories);
            int addedCount = existingCategories.size() - beforeCount;

            product.setCategories(existingCategories);
            Product savedProduct = productRepository.save(product);

            log.info("Product {} categories added: {} new categories (total: {})",
                    productId, addedCount, existingCategories.size());

            return ResponseEntity.ok(Map.of(
                    "message", "Categories added successfully",
                    "productId", productId,
                    "addedCount", addedCount,
                    "totalCategories", existingCategories.size(),
                    "categories", savedProduct.getCategories().stream()
                            .map(c -> Map.of(
                                "id", c.getId(),
                                "name", c.getName(),
                                "slug", c.getSlug()
                            ))
                            .collect(Collectors.toList())
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to add product categories: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to add product categories",
                        "details", e.getMessage()
                    ));
        }
    }

    /**
     * Remove categories from product
     * DELETE /api/admin/products/{productId}/categories
     *
     * Request body:
     * {
     *   "categoryIds": [1, 2]
     * }
     */
    @DeleteMapping("/{productId}/categories")
    public ResponseEntity<?> removeProductCategories(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        try {
            // Find product
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            // Extract category IDs from request
            @SuppressWarnings("unchecked")
            List<?> rawCategoryIds = (List<?>) request.get("categoryIds");

            if (rawCategoryIds == null || rawCategoryIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "categoryIds field is required and must not be empty"));
            }

            // Convert to List<Long>
            Set<Long> categoryIdsToRemove = new HashSet<>();
            for (Object id : rawCategoryIds) {
                if (id instanceof Integer) {
                    categoryIdsToRemove.add(((Integer) id).longValue());
                } else if (id instanceof Long) {
                    categoryIdsToRemove.add((Long) id);
                } else if (id != null) {
                    categoryIdsToRemove.add(Long.parseLong(id.toString()));
                }
            }

            // Remove categories
            Set<Category> existingCategories = product.getCategories();
            if (existingCategories == null || existingCategories.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "message", "Product has no categories to remove",
                        "productId", productId
                ));
            }

            int beforeCount = existingCategories.size();
            existingCategories.removeIf(c -> categoryIdsToRemove.contains(c.getId()));
            int removedCount = beforeCount - existingCategories.size();

            product.setCategories(existingCategories);
            Product savedProduct = productRepository.save(product);

            log.info("Product {} categories removed: {} categories deleted (remaining: {})",
                    productId, removedCount, existingCategories.size());

            return ResponseEntity.ok(Map.of(
                    "message", "Categories removed successfully",
                    "productId", productId,
                    "removedCount", removedCount,
                    "remainingCategories", existingCategories.size(),
                    "categories", savedProduct.getCategories().stream()
                            .map(c -> Map.of(
                                "id", c.getId(),
                                "name", c.getName(),
                                "slug", c.getSlug()
                            ))
                            .collect(Collectors.toList())
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to remove product categories: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to remove product categories",
                        "details", e.getMessage()
                    ));
        }
    }
}
