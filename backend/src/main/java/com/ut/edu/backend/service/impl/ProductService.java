package com.ut.edu.backend.service.impl;

import com.ut.edu.backend.model.Category;
import com.ut.edu.backend.model.Product;
import com.ut.edu.backend.model.ProductImage;
import com.ut.edu.backend.repository.CategoryRepository;
import com.ut.edu.backend.repository.ProductImageRepository;
import com.ut.edu.backend.repository.ProductRepository;
import com.ut.edu.backend.service.inter.ICloudinaryService;
import com.ut.edu.backend.service.inter.IProductService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Product Service with advanced search, filter, and pagination
 * Includes Redis caching for performance
 */
@Service
@Slf4j
@Transactional
public class ProductService implements IProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ICloudinaryService cloudinaryService;

    @Autowired
    private RedisProductCacheService productCacheService;

    /**
     * Get product by ID with caching
     */
    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        log.info("Fetching product by ID: {}", id);

        // Try to get from cache first
        Product cachedProduct = productCacheService.getCachedProduct(id);
        if (cachedProduct != null) {
            return Optional.of(cachedProduct);
        }

        // Cache miss - fetch from database
        Optional<Product> product = productRepository.findById(id);

        // Save to cache for future requests
        product.ifPresent(productCacheService::cacheProduct);

        return product;
    }

    /**
     * Get product by slug with caching
     */
    @Transactional(readOnly = true)
    public Optional<Product> getProductBySlug(String slug) {
        log.info("Fetching product by slug: {}", slug);

        // Try to get from cache first
        Product cachedProduct = productCacheService.getCachedProductBySlug(slug);
        if (cachedProduct != null) {
            return Optional.of(cachedProduct);
        }

        // Cache miss - fetch from database
        Optional<Product> product = productRepository.findBySlug(slug);

        // Save to cache for future requests (both by slug and by ID)
        product.ifPresent(p -> {
            productCacheService.cacheProductBySlug(slug, p);
            productCacheService.cacheProduct(p);
        });

        return product;
    }

    @Transactional(readOnly = true)
    public Page<Product> searchProducts(
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
    ) {
        log.info("Searching products - keyword: {}, category: {}, price: {}-{}, brand: {}, gender: {}, size: {}, color: {}, sort: {}",
                keyword, categoryId, minPrice, maxPrice, brand, gender, size, color, sortBy);

        // Generate cache key for this search
        String searchKey = productCacheService.generateSearchKey(
                keyword, categoryId, minPrice, maxPrice, brand, gender, size, color, sortBy,
                pageable.getPageNumber(), pageable.getPageSize()
        );

        // Try to get from cache first
        Page<Product> cachedResults = productCacheService.getCachedSearchResults(searchKey);
        if (cachedResults != null) {
            return cachedResults;
        }

        // Cache miss - perform database search
        Specification<Product> spec = (root, query, criteriaBuilder) -> {
                List<Predicate> predicates = new ArrayList<>();

                // Active products only
                predicates.add(criteriaBuilder.isTrue(root.get("active")));

                // Keyword search
                if (keyword != null && !keyword.trim().isEmpty()) {
                    String searchPattern = "%" + keyword.toLowerCase() + "%";
                    Predicate namePredicate = criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("name")), searchPattern);
                    Predicate descPredicate = criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("description")), searchPattern);
                    Predicate brandPredicate = criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("brand")), searchPattern);
                    predicates.add(criteriaBuilder.or(namePredicate, descPredicate, brandPredicate));
                }

                // Category filter
                if (categoryId != null) {
                    Join<Product, Category> categoryJoin = root.join("categories");
                    predicates.add(criteriaBuilder.equal(categoryJoin.get("id"), categoryId));
                }

                // Price range
                if (minPrice != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice));
                }
                if (maxPrice != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice));
                }

                // Brand filter
                if (brand != null && !brand.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.equal(
                            criteriaBuilder.lower(root.get("brand")),
                            brand.toLowerCase()
                    ));
                }

                // Gender filter
                if (gender != null && !gender.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.equal(
                            criteriaBuilder.lower(root.get("gender")),
                            gender.toLowerCase()
                    ));
                }

                // Size filter
                if (size != null && !size.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.isMember(size, root.get("availableSizes")));
                }

                // Color filter
                if (color != null && !color.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.isMember(color, root.get("availableColors")));
                }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Product> results = productRepository.findAll(spec, pageable);

        // Save results to cache
        productCacheService.cacheSearchResults(searchKey, results);

        return results;
    }

    /**
     * Get all active products
     */
    @Transactional(readOnly = true)
   
    public Page<Product> getAllActiveProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable);
    }

    /**
     * Get featured products with Redis caching
     */
    @Transactional(readOnly = true)
    public Page<Product> getFeaturedProducts(Pageable pageable) {
        log.info("Fetching featured products - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        // Try to get from cache first
        Page<Product> cachedResults = productCacheService.getCachedFeaturedProducts(
                pageable.getPageNumber(), pageable.getPageSize()
        );
        if (cachedResults != null) {
            return cachedResults;
        }

        // Cache miss - fetch from database
        Page<Product> results = productRepository.findByFeaturedTrueAndActiveTrue(pageable);

        // Save to cache
        productCacheService.cacheFeaturedProducts(results, pageable.getPageNumber(), pageable.getPageSize());

        return results;
    }

    /**
     * Get best sellers with Redis caching
     */
    @Transactional(readOnly = true)
    public Page<Product> getBestSellers(Pageable pageable) {
        log.info("Fetching best sellers - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        // Try to get from cache first
        Page<Product> cachedResults = productCacheService.getCachedBestSellers(
                pageable.getPageNumber(), pageable.getPageSize()
        );
        if (cachedResults != null) {
            return cachedResults;
        }

        // Cache miss - fetch from database
        Page<Product> results = productRepository.findBestSellers(pageable);

        // Save to cache
        productCacheService.cacheBestSellers(results, pageable.getPageNumber(), pageable.getPageSize());

        return results;
    }

    /**
     * Get newest products
     */
   
    public Page<Product> getNewestProducts(Pageable pageable) {
        return productRepository.findNewest(pageable);
    }

    /**
     * Get products on sale
     */
    
    public Page<Product> getProductsOnSale(Pageable pageable) {
        return productRepository.findOnSale(pageable);
    }

    /**
     * Get products by category
     */

    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable);
    }

    /**
     * Get products by gender with pagination
     * @param gender Gender filter ("Nam", "Nữ", "Women", etc.)
     * @param pageable Pagination parameters
     * @return Page of products matching the gender
     */
    public Page<Product> getProductsByGender(String gender, Pageable pageable) {
        log.info("Fetching products by gender: {}", gender);
        return productRepository.findByGenderIgnoreCaseAndActiveTrue(gender, pageable);
    }

    /**
     * Create a new product with images
     */
   
    public Product createProduct(Product product, MultipartFile[] imageFiles, String categorySlug) {
        log.info("Creating new product: {}", product.getName());

        // Validate and save product
        if (productRepository.existsBySlug(product.getSlug())) {
            throw new IllegalArgumentException("Product with slug already exists: " + product.getSlug());
        }
        if (productRepository.existsBySku(product.getSku())) {
            throw new IllegalArgumentException("Product with SKU already exists: " + product.getSku());
        }

        Product savedProduct = productRepository.save(product);

        // Upload images if provided
        if (imageFiles != null && imageFiles.length > 0) {
            if (cloudinaryService.validateImages(imageFiles)) {
                try {
                    List<ProductImage> productImages = cloudinaryService.uploadProductImages(
                            imageFiles, savedProduct, categorySlug
                    );

                    for (ProductImage image : productImages) {
                        savedProduct.addImage(image);
                    }

                    productImageRepository.saveAll(productImages);
                    log.info("Uploaded {} images for product: {}", productImages.size(), savedProduct.getId());

                } catch (Exception e) {
                    log.error("Failed to upload images for product: {}", savedProduct.getId(), e);
                    // Continue without images - they can be added later
                }
            }
        }

        return savedProduct;
    }

    /**
     * Update product
     */
    @Transactional
    public Product updateProduct(Long id, Product updatedProduct) {
        log.info("Updating product: {}", id);

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        String oldSlug = existingProduct.getSlug();

        // Update fields
        existingProduct.setName(updatedProduct.getName());
        existingProduct.setSlug(updatedProduct.getSlug());
        existingProduct.setDescription(updatedProduct.getDescription());
        existingProduct.setShortDescription(updatedProduct.getShortDescription());
        existingProduct.setPrice(updatedProduct.getPrice());
        existingProduct.setCompareAtPrice(updatedProduct.getCompareAtPrice());
        existingProduct.setStockQuantity(updatedProduct.getStockQuantity());
        existingProduct.setActive(updatedProduct.getActive());
        existingProduct.setFeatured(updatedProduct.getFeatured());
        existingProduct.setBrand(updatedProduct.getBrand());
        existingProduct.setMaterial(updatedProduct.getMaterial());
        existingProduct.setGender(updatedProduct.getGender());
        existingProduct.setAvailableSizes(updatedProduct.getAvailableSizes());
        existingProduct.setAvailableColors(updatedProduct.getAvailableColors());
        existingProduct.setMetaTitle(updatedProduct.getMetaTitle());
        existingProduct.setMetaDescription(updatedProduct.getMetaDescription());
        existingProduct.setMetaKeywords(updatedProduct.getMetaKeywords());

        Product savedProduct = productRepository.save(existingProduct);

        // Invalidate cache (product changed)
        productCacheService.invalidateProduct(id, oldSlug);
        if (!oldSlug.equals(updatedProduct.getSlug())) {
            // Slug changed, invalidate new slug too
            productCacheService.invalidateProduct(id, updatedProduct.getSlug());
        }

        return savedProduct;
    }

    /**
     * Add images to existing product
     */
    
    public Product addProductImages(Long productId, MultipartFile[] imageFiles, String categorySlug, String color) {
        log.info("Adding images to product: {} with color: {}", productId, color);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        if (cloudinaryService.validateImages(imageFiles)) {
            try {
                List<ProductImage> productImages = cloudinaryService.uploadProductImages(
                        imageFiles, product, categorySlug
                );

                // Set color for all uploaded images
                for (ProductImage image : productImages) {
                    if (color != null && !color.isBlank()) {
                        image.setColor(color);
                    }
                    product.addImage(image);
                }

                productImageRepository.saveAll(productImages);
                log.info("Added {} images to product: {} with color: {}", productImages.size(), productId, color);

            } catch (Exception e) {
                log.error("Failed to add images to product: {}", productId, e);
                throw new RuntimeException("Failed to upload images: " + e.getMessage());
            }
        }

        return product;
    }

    /**
     * Delete product image
     */
    
    public void deleteProductImage(Long imageId) {
        log.info("Deleting product image: {}", imageId);

        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));

        // Delete from Cloudinary
        cloudinaryService.deleteImage(image.getCloudinaryPublicId());

        // Delete from database
        productImageRepository.delete(image);
    }

    /**
     * Delete product
     */
    @Transactional
    public void deleteProduct(Long id) {
        log.info("Deleting product: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        String slug = product.getSlug();

        // Delete all images from Cloudinary
        for (ProductImage image : product.getImages()) {
            cloudinaryService.deleteImage(image.getCloudinaryPublicId());
        }

        productRepository.delete(product);

        // Invalidate cache (product deleted)
        productCacheService.invalidateProduct(id, slug);
    }

    /**
     * Increment product view count
     */
   
    public void incrementViewCount(Long productId) {
        productRepository.findById(productId).ifPresent(product -> {
            product.incrementViewCount();
            productRepository.save(product);
        });
    }

    /**
     * Update stock quantity
     */
   
    public void updateStock(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        if (quantity < 0) {
            product.decrementStock(Math.abs(quantity));
        } else {
            product.incrementStock(quantity);
        }

        productRepository.save(product);
        log.info("Updated stock for product {}: new stock = {}", productId, product.getStockQuantity());
    }

    /**
     * Get all available brands with Redis caching
     */
    @Transactional(readOnly = true)
    public List<String> getAllBrands() {
        // Try cache first
        List<String> cachedBrands = productCacheService.getCachedFilterOptions("brands");
        if (cachedBrands != null) {
            return cachedBrands;
        }

        // Cache miss - fetch from database
        List<String> brands = productRepository.findAllBrands();
        productCacheService.cacheFilterOptions("brands", brands);
        return brands;
    }

    /**
     * Get all available sizes with Redis caching
     */
    @Transactional(readOnly = true)
    public List<String> getAllSizes() {
        // Try cache first
        List<String> cachedSizes = productCacheService.getCachedFilterOptions("sizes");
        if (cachedSizes != null) {
            return cachedSizes;
        }

        // Cache miss - fetch from database
        List<String> sizes = productRepository.findAllSizes();
        productCacheService.cacheFilterOptions("sizes", sizes);
        return sizes;
    }

    /**
     * Get all available colors with Redis caching
     */
    @Transactional(readOnly = true)
    public List<String> getAllColors() {
        // Try cache first
        List<String> cachedColors = productCacheService.getCachedFilterOptions("colors");
        if (cachedColors != null) {
            return cachedColors;
        }

        // Cache miss - fetch from database
        List<String> colors = productRepository.findAllColors();
        productCacheService.cacheFilterOptions("colors", colors);
        return colors;
    }

    
}
