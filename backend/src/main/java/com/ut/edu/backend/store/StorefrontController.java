package com.ut.edu.backend.store;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public storefront API - one storefront per store, addressed by slug:
 * /api/stores/{slug}/products, /api/stores/{slug}/categories ...
 *
 * TenantResolverFilter resolves {slug} into TenantContext BEFORE the
 * controller runs, so every JPQL/derived query here is automatically scoped
 * to that store by the Hibernate tenant filter. findById bypasses the filter
 * and is therefore verified explicitly.
 */
@RestController
@RequestMapping("/stores/{slug}")
@RequiredArgsConstructor
@Slf4j
public class StorefrontController {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TenantGuard tenantGuard;

    /**
     * Public store profile
     * GET /api/stores/{slug}
     */
    @GetMapping
    public ResponseEntity<?> getStoreInfo(@PathVariable String slug) {
        return storeRepository.findBySlugAndStatusNot(slug, StoreStatus.SUSPENDED)
                .<ResponseEntity<?>>map(store -> ResponseEntity.ok(Map.of(
                        "id", store.getId(),
                        "name", store.getName(),
                        "slug", store.getSlug(),
                        "logoUrl", store.getLogoUrl() != null ? store.getLogoUrl() : "",
                        "phone", store.getPhone() != null ? store.getPhone() : "",
                        "address", store.getAddress() != null ? store.getAddress() : ""
                )))
                .orElseGet(() -> storeNotFound(slug));
    }

    /**
     * Products of this store (active only)
     * GET /api/stores/{slug}/products?page=0&size=20&sortBy=createdAt&sortDirection=DESC
     */
    @GetMapping("/products")
    public ResponseEntity<?> getStoreProducts(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        if (!tenantResolved()) {
            return storeNotFound(slug);
        }

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        // Tenant filter scopes this to the resolved store
        Page<Product> products = productRepository.findByActiveTrue(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("products", products.getContent());
        response.put("currentPage", products.getNumber());
        response.put("totalItems", products.getTotalElements());
        response.put("totalPages", products.getTotalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * One product of this store
     * GET /api/stores/{slug}/products/{productId}
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<?> getStoreProduct(@PathVariable String slug, @PathVariable Long productId) {
        if (!tenantResolved()) {
            return storeNotFound(slug);
        }

        // findById bypasses the tenant filter -> verify ownership explicitly
        return productRepository.findById(productId)
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .filter(p -> tenantGuard.isCurrentStore(p.getStore()))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Product not found: " + productId)));
    }

    /**
     * Categories of this store (active only)
     * GET /api/stores/{slug}/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getStoreCategories(@PathVariable String slug) {
        if (!tenantResolved()) {
            return storeNotFound(slug);
        }

        // Tenant filter scopes this to the resolved store
        List<Category> categories = categoryRepository.findByActiveTrueOrderByDisplayOrderAsc();
        return ResponseEntity.ok(Map.of("categories", categories, "count", categories.size()));
    }

    /** TenantResolverFilter leaves the context empty for unknown/suspended slugs. */
    private boolean tenantResolved() {
        return TenantContext.hasStore();
    }

    private ResponseEntity<?> storeNotFound(String slug) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Store not found: " + slug));
    }
}
