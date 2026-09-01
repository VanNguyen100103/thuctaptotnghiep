package com.ut.edu.backend.cart;

import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductImage;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.StoreRepository;
import com.ut.edu.backend.store.StoreStatus;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.product.ProductRepository;
import com.ut.edu.backend.user.UserRepository;
import com.ut.edu.backend.security.AuthorizationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;


import java.util.*;
import java.util.stream.Collectors;

/**
 * Cart Controller
 * Handles shopping cart operations with size and color selection
 */
@RestController
@RequestMapping("/cart")
@Slf4j
@Transactional
public class CartController {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private com.ut.edu.backend.cart.RedisCartCacheService cartCacheService;

    /**
     * Get current user's cart for the given store
     * GET /api/cart?storeSlug=...
     *
     * A user can have one cart per store (uk_carts_store_user), so the
     * client must say which store's cart it wants.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCart(@RequestParam String storeSlug) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();
            Store store = resolveStore(storeSlug);
            Cart cart = getOrCreateCart(currentUserId, store.getId());

            Map<String, Object> response = buildCartResponse(cart);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get cart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve cart"));
        }
    }

    /**
     * Add item to cart with size and color selection
     * POST /api/cart/items
     *
     * Request body:
     * {
     *   "productId": 1,
     *   "quantity": 2,
     *   "size": "M",
     *   "color": "Black"
     * }
     */
    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addToCart(@Valid @RequestBody AddToCartRequest request) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            // Validate product exists
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.getProductId()));

            // Validate product is active and in stock
            if (!product.getActive()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product is not available"));
            }

            if (!product.isInStock()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product is out of stock"));
            }

            // Validate size if provided
            if (request.getSize() != null && !request.getSize().trim().isEmpty()) {
                if (!product.getAvailableSizes().contains(request.getSize())) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Size '" + request.getSize() + "' is not available for this product"));
                }
            }

            // Validate color if provided
            if (request.getColor() != null && !request.getColor().trim().isEmpty()) {
                if (!product.getAvailableColors().contains(request.getColor())) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Color '" + request.getColor() + "' is not available for this product"));
                }
            }

            // Validate stock quantity
            if (product.getStockQuantity() < request.getQuantity()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only " + product.getStockQuantity() + " items available in stock"));
            }

            // Get or create the cart for THIS product's store - a user can
            // have a separate cart per store (uk_carts_store_user)
            Cart cart = getOrCreateCart(currentUserId, product.getStore().getId());

            // Check if same product with same size and color already exists in cart
            Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductIdAndSizeAndColor(
                    cart.getId(),
                    product.getId(),
                    request.getSize(),
                    request.getColor()
            );

            CartItem cartItem;
            if (existingItem.isPresent()) {
                // Update existing item quantity
                cartItem = existingItem.get();
                int newQuantity = cartItem.getQuantity() + request.getQuantity();

                // Check total quantity against stock
                if (product.getStockQuantity() < newQuantity) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Cannot add more items. Only " + product.getStockQuantity() + " items available"));
                }

                cartItem.setQuantity(newQuantity);
                cartItem = cartItemRepository.save(cartItem);

            } else {
                // Create new cart item
                cartItem = CartItem.builder()
                        .cart(cart)
                        .product(product)
                        .quantity(request.getQuantity())
                        .selectedSize(request.getSize())
                        .selectedColor(request.getColor())
                        .priceAtAdd(product.getPrice())
                        .build();

                cartItem = cartItemRepository.save(cartItem);
                cart.addItem(cartItem);
            }

            // Refresh cart
            cart = cartRepository.findById(cart.getId()).orElseThrow();

            // Update cache after adding item
            cartCacheService.updateCartCache(currentUserId, product.getStore().getId(), cart);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Product added to cart successfully",
                            "cart", buildCartResponse(cart),
                            "itemAdded", buildCartItemResponse(cartItem)
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to add item to cart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add item to cart"));
        }
    }

    /**
     * Get specific cart item by ID
     * GET /api/cart/items/{itemId}
     */
    @GetMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCartItem(@PathVariable Long itemId) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            CartItem cartItem = cartItemRepository.findById(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + itemId));

            // Verify ownership - prevent IDOR
            if (!cartItem.getCart().getUser().getId().equals(currentUserId)) {
                log.warn("IDOR attempt: User {} tried to access cart item {} owned by user {}",
                        currentUserId, itemId, cartItem.getCart().getUser().getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            return ResponseEntity.ok(buildCartItemResponse(cartItem));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get cart item: {}", itemId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get cart item"));
        }
    }

    /**
     * Update cart item quantity
     * PUT /api/cart/items/{itemId}
     *
     * Request body:
     * {
     *   "quantity": 3
     * }
     */
    @PutMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateCartItem(
            @PathVariable Long itemId,
            @RequestBody Map<String, Integer> request) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            CartItem cartItem = cartItemRepository.findById(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + itemId));

            // Verify ownership
            if (!cartItem.getCart().getUser().getId().equals(currentUserId)) {
                log.warn("IDOR attempt: User {} tried to update cart item {} owned by user {}",
                        currentUserId, itemId, cartItem.getCart().getUser().getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            Integer newQuantity = request.get("quantity");
            if (newQuantity == null || newQuantity < 1) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Quantity must be at least 1"));
            }

            // Check stock availability
            Product product = cartItem.getProduct();
            if (product.getStockQuantity() < newQuantity) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only " + product.getStockQuantity() + " items available in stock"));
            }

            cartItem.setQuantity(newQuantity);
            cartItem = cartItemRepository.save(cartItem);

            // Get updated cart
            Cart cart = cartItem.getCart();

            // Update cache after quantity change
            cartCacheService.updateCartCache(currentUserId, cart.getStore().getId(), cart);

            return ResponseEntity.ok(Map.of(
                    "message", "Cart item updated successfully",
                    "cart", buildCartResponse(cart),
                    "itemUpdated", buildCartItemResponse(cartItem)
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update cart item: {}", itemId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update cart item"));
        }
    }

    /**
     * Remove item from cart
     * DELETE /api/cart/items/{itemId}
     */
    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> removeCartItem(@PathVariable Long itemId) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            CartItem cartItem = cartItemRepository.findById(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + itemId));

            // Verify ownership
            if (!cartItem.getCart().getUser().getId().equals(currentUserId)) {
                log.warn("IDOR attempt: User {} tried to delete cart item {} owned by user {}",
                        currentUserId, itemId, cartItem.getCart().getUser().getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            Long cartId = cartItem.getCart().getId();

            // Delete and flush immediately to ensure it's committed
            cartItemRepository.delete(cartItem);
            cartItemRepository.flush();

            // Get updated cart (refresh from database after delete)
            Cart cart = cartRepository.findById(cartId).orElseThrow();

            // Update cache after removing item
            cartCacheService.updateCartCache(currentUserId, cart.getStore().getId(), cart);

            return ResponseEntity.ok(Map.of(
                    "message", "Item removed from cart successfully",
                    "cart", buildCartResponse(cart)
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to remove cart item: {}", itemId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to remove cart item"));
        }
    }

    /**
     * Clear entire cart
     * DELETE /api/cart/clear?storeSlug=...
     */
    @DeleteMapping("/clear")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> clearCart(@RequestParam String storeSlug) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();
            Store store = resolveStore(storeSlug);
            Cart cart = getOrCreateCart(currentUserId, store.getId());

            Long cartId = cart.getId();

            // Delete all items and flush to ensure immediate commit
            cartItemRepository.deleteByCartId(cartId);
            cartItemRepository.flush();

            // Refresh cart from database to get updated state
            cart = cartRepository.findById(cartId).orElseThrow();

            // Update cache after clearing cart
            cartCacheService.updateCartCache(currentUserId, store.getId(), cart);

            return ResponseEntity.ok(Map.of(
                    "message", "Cart cleared successfully",
                    "cart", buildCartResponse(cart)
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to clear cart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to clear cart"));
        }
    }

    /**
     * Get cart item count
     * GET /api/cart/count?storeSlug=...
     */
    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCartCount(@RequestParam String storeSlug) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();
            Store store = resolveStore(storeSlug);

            // Try to get count from cache first (lightweight)
            Integer cachedCount = cartCacheService.getCachedCartCount(currentUserId, store.getId());
            if (cachedCount != null) {
                return ResponseEntity.ok(Map.of("count", cachedCount));
            }

            // Cache miss - get from database
            Cart cart = getOrCreateCart(currentUserId, store.getId());
            Integer count = cart.getTotalItems();

            // Cache count for fast future access
            cartCacheService.cacheCartCount(currentUserId, store.getId(), count);

            return ResponseEntity.ok(Map.of("count", count));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get cart count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get cart count"));
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get or create the user's cart for a specific store. A user can have
     * one cart per store (uk_carts_store_user) - the cart is store-bound
     * from the moment it's created, never left null and backfilled later.
     */
    private Cart getOrCreateCart(Long userId, Long storeId) {
        return cartRepository.findByUserIdAndStoreId(userId, storeId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found"));

                    Cart newCart = Cart.builder()
                            .user(user)
                            .store(storeRepository.getReferenceById(storeId))
                            .active(true)
                            .build();

                    return cartRepository.save(newCart);
                });
    }

    /**
     * Resolve a storefront slug to its Store, the same way
     * TenantResolverFilter does for the public /stores/{slug}/** routes -
     * a suspended store resolves as "not found".
     */
    private Store resolveStore(String slug) {
        return storeRepository.findBySlugAndStatusNot(slug, StoreStatus.SUSPENDED)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + slug));
    }

    /**
     * Build cart response with all details
     */
    private Map<String, Object> buildCartResponse(Cart cart) {
        List<Map<String, Object>> items = cart.getItems().stream()
                .map(this::buildCartItemResponse)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("id", cart.getId());
        response.put("items", items);
        response.put("totalItems", cart.getTotalItems());
        response.put("totalPrice", cart.getTotalPrice());
        response.put("createdAt", cart.getCreatedAt());
        response.put("updatedAt", cart.getUpdatedAt());

        return response;
    }

    /**
     * Build cart item response
     */
    private Map<String, Object> buildCartItemResponse(CartItem item) {
        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("id", item.getId());
        itemMap.put("productId", item.getProduct().getId());
        itemMap.put("productName", item.getProduct().getName());
        itemMap.put("productSlug", item.getProduct().getSlug());
        itemMap.put("productSku", item.getProduct().getSku());
        itemMap.put("quantity", item.getQuantity());
        itemMap.put("size", item.getSelectedSize());
        itemMap.put("color", item.getSelectedColor());
        itemMap.put("priceAtAdd", item.getPriceAtAdd());
        itemMap.put("subtotal", item.getSubtotal());
        itemMap.put("stockAvailable", item.getProduct().getStockQuantity());

        // Add product image if available
        if (!item.getProduct().getImages().isEmpty()) {
            ProductImage firstImage = item.getProduct().getImages().stream()
                    .findFirst()
                    .orElse(null);
            if (firstImage != null) {
                itemMap.put("productImage", firstImage.getImageUrl());
            }
        }

        return itemMap;
    }

    // ==================== REQUEST DTOs ====================

    /**
     * Request DTO for adding item to cart
     */
    @lombok.Data
    public static class AddToCartRequest {
        @jakarta.validation.constraints.NotNull(message = "Product ID is required")
        private Long productId;

        @jakarta.validation.constraints.NotNull(message = "Quantity is required")
        @jakarta.validation.constraints.Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        private String size;

        private String color;
    }
}
