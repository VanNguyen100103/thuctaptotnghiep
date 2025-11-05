package com.ut.edu.backend.service.inter;

import com.ut.edu.backend.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Product Service Interface
 * Manages product operations with caching, search, and filtering
 */
public interface IProductService {

    // Read operations
    Optional<Product> getProductById(Long id);
    Optional<Product> getProductBySlug(String slug);
    Page<Product> getAllActiveProducts(Pageable pageable);
    Page<Product> getFeaturedProducts(Pageable pageable);
    Page<Product> getBestSellers(Pageable pageable);
    Page<Product> getNewestProducts(Pageable pageable);
    Page<Product> getProductsOnSale(Pageable pageable);
    Page<Product> getProductsByCategory(Long categoryId, Pageable pageable);
    Page<Product> getProductsByGender(String gender, Pageable pageable);

    // Search and filter
    Page<Product> searchProducts(
        String keyword,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String brand,
        String gender,
        String size,
        String color,
        String sortBy,
        Pageable pageable
    );

    // Create and update operations
    Product createProduct(Product product, MultipartFile[] imageFiles, String categorySlug);
    Product updateProduct(Long id, Product updatedProduct);
    Product addProductImages(Long productId, MultipartFile[] imageFiles, String categorySlug, String color);
    void deleteProductImage(Long imageId);
    void deleteProduct(Long id);

    // Stock management
    void incrementViewCount(Long productId);
    void updateStock(Long productId, Integer quantity);

    // Filter options
    List<String> getAllBrands();
    List<String> getAllSizes();
    List<String> getAllColors();
}
