package com.ut.edu.backend.product;

import com.ut.edu.backend.common.HtmlEntityDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Product Controller
 * Handles product CRUD, search, filter, and pagination
 */
@RestController
@RequestMapping("/products")
@Slf4j
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * Get all active products with pagination
     * GET /api/products?page=0&size=20&sort=name
     */
    @GetMapping
    public ResponseEntity<Page<Product>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<Product> products = productService.getAllActiveProducts(pageable);

        return ResponseEntity.ok(products);
    }

    /**
     * Search and filter products
     * GET /api/products/search?keyword=shirt&category=1&minPrice=10&maxPrice=100...
     */
    @GetMapping("/search")
    public ResponseEntity<Page<Product>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String color,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size_param,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        // Decode HTML entities in keyword if present
        String decodedKeyword = keyword != null ? HtmlEntityDecoder.decode(keyword) : null;

        log.info("Searching products - keyword: {} (decoded: {}), category: {}, price: {}-{}, brand: {}, gender: {}, size: {}, color: {}",
                keyword, decodedKeyword, categoryId, minPrice, maxPrice, brand, gender, size, color);

        Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size_param, Sort.by(direction, sortBy));

        Page<Product> products = productService.searchProducts(
                decodedKeyword, categoryId, minPrice, maxPrice,
                brand, gender, size, color, sortBy, pageable
        );

        return ResponseEntity.ok(products);
    }

    /**
     * Get product by ID
     * GET /api/products/1
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(product -> {
                    // Increment view count
                    productService.incrementViewCount(id);
                    return ResponseEntity.ok(product);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get product by slug
     * GET /api/products/slug/men-t-shirt
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<?> getProductBySlug(@PathVariable String slug) {
        return productService.getProductBySlug(slug)
                .map(product -> {
                    // Increment view count
                    productService.incrementViewCount(product.getId());
                    return ResponseEntity.ok(product);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get featured products
     * GET /api/products/featured
     */
    @GetMapping("/featured")
    public ResponseEntity<Page<Product>> getFeaturedProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getFeaturedProducts(pageable);

        return ResponseEntity.ok(products);
    }

    /**
     * Get best sellers
     * GET /api/products/bestsellers
     */
    @GetMapping("/bestsellers")
    public ResponseEntity<Page<Product>> getBestSellers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getBestSellers(pageable);

        return ResponseEntity.ok(products);
    }

    /**
     * Get newest products
     * GET /api/products/newest
     */
    @GetMapping("/newest")
    public ResponseEntity<Page<Product>> getNewestProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getNewestProducts(pageable);

        return ResponseEntity.ok(products);
    }

    /**
     * Get products on sale
     * GET /api/products/sale
     */
    @GetMapping("/sale")
    public ResponseEntity<Page<Product>> getProductsOnSale(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getProductsOnSale(pageable);

        return ResponseEntity.ok(products);
    }

    /**
     * Get products by category
     * GET /api/products/category/1
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<Product>> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getProductsByCategory(categoryId, pageable);

        return ResponseEntity.ok(products);
    }

    /**
     * Get products by gender
     * GET /api/products/filter/gender/Nam
     * GET /api/products/filter/gender/Nữ
     * GET /api/products/filter/gender/Women
     */
    @GetMapping("/filters/gender/{gender}")
    public ResponseEntity<Page<Product>> getProductsByGender(
            @PathVariable String gender,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        log.info("Fetching products by gender: {}, page: {}, size: {}", gender, page, size);

        Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<Product> products = productService.getProductsByGender(gender, pageable);

        return ResponseEntity.ok(products);
    }

    /**
     * Get all brands
     * GET /api/products/filters/brands
     */
    @GetMapping("/filters/brands")
    public ResponseEntity<List<String>> getAllBrands() {
        List<String> brands = productService.getAllBrands();
        return ResponseEntity.ok(brands);
    }

    /**
     * Get all sizes
     * GET /api/products/filters/sizes
     */
    @GetMapping("/filters/sizes")
    public ResponseEntity<List<String>> getAllSizes() {
        List<String> sizes = productService.getAllSizes();
        return ResponseEntity.ok(sizes);
    }

    /**
     * Get all colors
     * GET /api/products/filters/colors
     */
    @GetMapping("/filters/colors")
    public ResponseEntity<List<String>> getAllColors() {
        List<String> colors = productService.getAllColors();
        return ResponseEntity.ok(colors);
    }

  

    

    /**
     * Add images to product (Admin only)
     * POST /api/products/1/images
     */
    @PostMapping("/{id}/images")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<?> addProductImages(
            @PathVariable Long id,
            @RequestPart("images") MultipartFile[] images,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) String color
    ) {
        log.info("Adding images to product: {} with color: {}", id, color);

        try {
            Product product = productService.addProductImages(id, images, categorySlug, color);

            return ResponseEntity.ok(Map.of(
                    "message", "Images added successfully",
                    "product", product
            ));

        } catch (Exception e) {
            log.error("Error adding images", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete product image (Admin only)
     * DELETE /api/products/images/1
     */
    @DeleteMapping("/images/{imageId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<?> deleteProductImage(@PathVariable Long imageId) {
        log.info("Deleting product image: {}", imageId);

        try {
            productService.deleteProductImage(imageId);

            return ResponseEntity.ok(Map.of(
                    "message", "Image deleted successfully"
            ));

        } catch (Exception e) {
            log.error("Error deleting image", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
