package com.ut.edu.backend.security;

import com.ut.edu.backend.model.*;
import com.ut.edu.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Authorization Service
 * Prevents IDOR (Insecure Direct Object Reference) attacks
 *
 * IDOR Example:
 * - User A tries to access User B's order by changing order ID in URL
 * - This service checks if User A owns that order before allowing access
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final WishlistRepository wishlistRepository;

    /**
     * Get current authenticated user
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new SecurityException("User not found: " + username));
    }

    /**
     * Get current user ID
     */
    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    /**
     * Check if current user has role
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    /**
     * Check if current user is admin
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * IDOR Protection: Check if user can access/modify this user profile
     */
    public boolean canAccessUser(Long userId) {
        // Admin can access any user
        if (isAdmin()) {
            return true;
        }

        // User can only access their own profile
        Long currentUserId = getCurrentUserId();
        boolean canAccess = currentUserId.equals(userId);

        if (!canAccess) {
            log.warn("IDOR attempt detected: User {} tried to access User {}", currentUserId, userId);
        }

        return canAccess;
    }

    /**
     * IDOR Protection: Check if user can access/modify this order
     */
    public boolean canAccessOrder(Long orderId) {
        if (isAdmin()) {
            return true;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new SecurityException("Order not found"));

        Long currentUserId = getCurrentUserId();
        boolean canAccess = order.getUser().getId().equals(currentUserId);

        if (!canAccess) {
            log.warn("IDOR attempt: User {} tried to access Order {} owned by User {}",
                    currentUserId, orderId, order.getUser().getId());
        }

        return canAccess;
    }

    /**
     * IDOR Protection: Check if user can access/modify this cart
     */
    public boolean canAccessCart(Long cartId) {
        if (isAdmin()) {
            return true;
        }

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new SecurityException("Cart not found"));

        Long currentUserId = getCurrentUserId();
        boolean canAccess = cart.getUser().getId().equals(currentUserId);

        if (!canAccess) {
            log.warn("IDOR attempt: User {} tried to access Cart {} owned by User {}",
                    currentUserId, cartId, cart.getUser().getId());
        }

        return canAccess;
    }

    /**
     * IDOR Protection: Check if user can access/modify this address
     */
    public boolean canAccessAddress(Long addressId) {
        if (isAdmin()) {
            return true;
        }

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new SecurityException("Address not found"));

        Long currentUserId = getCurrentUserId();
        boolean canAccess = address.getUser().getId().equals(currentUserId);

        if (!canAccess) {
            log.warn("IDOR attempt: User {} tried to access Address {} owned by User {}",
                    currentUserId, addressId, address.getUser().getId());
        }

        return canAccess;
    }

    /**
     * IDOR Protection: Check if user can access/modify this wishlist
     */
    public boolean canAccessWishlist(Long wishlistId) {
        if (isAdmin()) {
            return true;
        }

        Wishlist wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow(() -> new SecurityException("Wishlist not found"));

        Long currentUserId = getCurrentUserId();
        boolean canAccess = wishlist.getUser().getId().equals(currentUserId);

        if (!canAccess) {
            log.warn("IDOR attempt: User {} tried to access Wishlist {} owned by User {}",
                    currentUserId, wishlistId, wishlist.getUser().getId());
        }

        return canAccess;
    }

    /**
     * Ensure current user can access resource, throw exception if not
     */
    public void requireUserAccess(Long userId) {
        if (!canAccessUser(userId)) {
            throw new SecurityException("Access denied: You don't have permission to access this user");
        }
    }

    /**
     * Ensure current user can access order, throw exception if not
     */
    public void requireOrderAccess(Long orderId) {
        if (!canAccessOrder(orderId)) {
            throw new SecurityException("Access denied: You don't have permission to access this order");
        }
    }

    /**
     * Ensure current user can access cart, throw exception if not
     */
    public void requireCartAccess(Long cartId) {
        if (!canAccessCart(cartId)) {
            throw new SecurityException("Access denied: You don't have permission to access this cart");
        }
    }

    /**
     * Ensure current user can access address, throw exception if not
     */
    public void requireAddressAccess(Long addressId) {
        if (!canAccessAddress(addressId)) {
            throw new SecurityException("Access denied: You don't have permission to access this address");
        }
    }

    /**
     * Ensure current user can access wishlist, throw exception if not
     */
    public void requireWishlistAccess(Long wishlistId) {
        if (!canAccessWishlist(wishlistId)) {
            throw new SecurityException("Access denied: You don't have permission to access this wishlist");
        }
    }

    /**
     * Ensure current user is admin
     */
    public void requireAdmin() {
        if (!isAdmin()) {
            Long currentUserId = getCurrentUserId();
            log.warn("Unauthorized admin access attempt by User {}", currentUserId);
            throw new SecurityException("Access denied: Admin privileges required");
        }
    }
}
