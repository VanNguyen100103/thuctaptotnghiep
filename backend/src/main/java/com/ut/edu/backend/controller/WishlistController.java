package com.ut.edu.backend.controller;

import com.ut.edu.backend.model.Product;
import com.ut.edu.backend.model.Wishlist;
import com.ut.edu.backend.repository.ProductRepository;
import com.ut.edu.backend.repository.UserRepository;
import com.ut.edu.backend.repository.WishlistRepository;
import com.ut.edu.backend.security.AuthorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wishlist Controller
 * Manages user wishlists with IDOR protection
 */
@RestController
@RequestMapping("/wishlist")
@Slf4j
@Transactional
public class WishlistController {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorizationService authorizationService;

    /**
     * Get all wishlist items for current user
     * GET /api/wishlist
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getUserWishlist() {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();
            List<Wishlist> wishlistItems = wishlistRepository.findByUserIdWithProductDetails(currentUserId);

            // Map to response format with product details
            List<Map<String, Object>> response = wishlistItems.stream()
                    .map(item -> {
                        Map<String, Object> itemMap = new java.util.HashMap<>();
                        itemMap.put("id", item.getId());
                        itemMap.put("product", item.getProduct());
                        itemMap.put("addedAt", item.getCreatedAt());
                        return itemMap;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> resultMap = new java.util.HashMap<>();
            resultMap.put("wishlistItems", response);
            resultMap.put("count", response.size());
            return ResponseEntity.ok(resultMap);

        } catch (Exception e) {
            log.error("Failed to get user wishlist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve wishlist"));
        }
    }

    /**
     * Add product to wishlist
     * POST /api/wishlist
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addToWishlist(@RequestBody Map<String, Object> request) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();
            Long productId = Long.valueOf(request.get("productId").toString());

            // Check if product exists
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            // Check if already in wishlist
            if (wishlistRepository.existsByUserIdAndProductId(currentUserId, productId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product already in wishlist"));
            }

            // Create wishlist item
            Wishlist wishlistItem = Wishlist.builder()
                    .user(userRepository.findById(currentUserId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found")))
                    .product(product)
                    .build();

            Wishlist savedItem = wishlistRepository.save(wishlistItem);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Product added to wishlist successfully",
                            "wishlistItem", Map.of(
                                    "id", savedItem.getId(),
                                    "product", savedItem.getProduct()
                            )
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to add product to wishlist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add product to wishlist"));
        }
    }

    /**
     * Remove product from wishlist (with IDOR protection)
     * DELETE /api/wishlist/{wishlistId}
     */
    @DeleteMapping("/{wishlistId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> removeFromWishlist(@PathVariable Long wishlistId) {
        try {
            // IDOR Protection: Check if current user can delete this wishlist item
            authorizationService.requireWishlistAccess(wishlistId);

            wishlistRepository.deleteById(wishlistId);

            return ResponseEntity.ok(Map.of(
                    "message", "Product removed from wishlist successfully"
            ));

        } catch (SecurityException e) {
            log.warn("IDOR attempt detected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to remove product from wishlist: {}", wishlistId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to remove product from wishlist"));
        }
    }

    /**
     * Remove product by product ID (convenience method)
     * DELETE /api/wishlist/product/{productId}
     */
    @DeleteMapping("/product/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> removeProductFromWishlist(@PathVariable Long productId) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            // Check if product exists in wishlist
            if (!wishlistRepository.existsByUserIdAndProductId(currentUserId, productId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product not found in wishlist"));
            }

            wishlistRepository.deleteByUserIdAndProductId(currentUserId, productId);

            return ResponseEntity.ok(Map.of(
                    "message", "Product removed from wishlist successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to remove product from wishlist: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to remove product from wishlist"));
        }
    }

    /**
     * Check if product is in wishlist
     * GET /api/wishlist/check/{productId}
     */
    @GetMapping("/check/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> checkProductInWishlist(@PathVariable Long productId) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();
            boolean inWishlist = wishlistRepository.existsByUserIdAndProductId(currentUserId, productId);

            return ResponseEntity.ok(Map.of(
                    "inWishlist", inWishlist
            ));

        } catch (Exception e) {
            log.error("Failed to check product in wishlist: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to check wishlist status"));
        }
    }

    /**
     * Clear entire wishlist
     * DELETE /api/wishlist/clear
     */
    @DeleteMapping("/clear")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> clearWishlist() {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();
            wishlistRepository.deleteByUserId(currentUserId);

            return ResponseEntity.ok(Map.of(
                    "message", "Wishlist cleared successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to clear wishlist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to clear wishlist"));
        }
    }
}
