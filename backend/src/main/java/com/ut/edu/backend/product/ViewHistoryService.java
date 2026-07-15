package com.ut.edu.backend.product;

import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service implementation for product view history tracking
 * Supports both authenticated users and anonymous sessions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ViewHistoryService {

    private final ProductViewRepository productViewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Maximum number of view records to keep per user/session
     */
    private static final int MAX_VIEWS_PER_USER = 100;

    @Transactional
    public void trackProductView(TrackViewRequest request, Long userId) {
        log.debug("Tracking product view: productId={}, userId={}, sessionId={}",
                request.getProductId(), userId, request.getSessionId());

        // Validate product exists
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + request.getProductId()));

        ProductView productView;

        if (userId != null) {
            // Authenticated user
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

            // Check if this user already viewed this product
            Optional<ProductView> existingView = productViewRepository.findByUserAndProduct(userId, request.getProductId());

            if (existingView.isPresent()) {
                // Update existing view with new timestamp
                productView = existingView.get();
                productView.setViewedAt(LocalDateTime.now());
                productView.setIpAddress(request.getIpAddress());
                productView.setUserAgent(request.getUserAgent());
                log.debug("Updating existing view record for user {} and product {}", userId, request.getProductId());
            } else {
                // Create new view record
                productView = ProductView.builder()
                        .store(product.getStore()) // same store as the viewed product
                        .user(user)
                        .product(product)
                        .sessionId(null) // No session ID for authenticated users
                        .ipAddress(request.getIpAddress())
                        .userAgent(request.getUserAgent())
                        .viewedAt(LocalDateTime.now())
                        .build();
                log.debug("Creating new view record for user {} and product {}", userId, request.getProductId());
            }
        } else {
            // Anonymous user
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                // Generate session ID if not provided
                sessionId = "anon-" + UUID.randomUUID().toString();
                log.debug("Generated new session ID: {}", sessionId);
            }

            // Check if this session already viewed this product
            Optional<ProductView> existingView = productViewRepository.findBySessionAndProduct(sessionId, request.getProductId());

            if (existingView.isPresent()) {
                // Update existing view with new timestamp
                productView = existingView.get();
                productView.setViewedAt(LocalDateTime.now());
                productView.setIpAddress(request.getIpAddress());
                productView.setUserAgent(request.getUserAgent());
                log.debug("Updating existing view record for session {} and product {}", sessionId, request.getProductId());
            } else {
                // Create new view record
                productView = ProductView.builder()
                        .store(product.getStore()) // same store as the viewed product
                        .user(null) // No user for anonymous sessions
                        .product(product)
                        .sessionId(sessionId)
                        .ipAddress(request.getIpAddress())
                        .userAgent(request.getUserAgent())
                        .viewedAt(LocalDateTime.now())
                        .build();
                log.debug("Creating new view record for session {} and product {}", sessionId, request.getProductId());
            }
        }

        productViewRepository.save(productView);
        log.info("Product view tracked successfully: productId={}, userId={}, sessionId={}",
                request.getProductId(), userId, request.getSessionId());
    }

    @Transactional(readOnly = true)
    public List<RecentlyViewedResponse> getRecentlyViewedProducts(Long userId, int limit) {
        log.debug("Fetching recently viewed products for user {}, limit={}", userId, limit);

        Pageable pageable = PageRequest.of(0, limit);
        List<ProductView> productViews = productViewRepository.findRecentlyViewedByUser(userId, pageable);

        log.debug("Found {} recently viewed products for user {}", productViews.size(), userId);

        return productViews.stream()
                .map(pv -> RecentlyViewedResponse.fromProduct(pv.getProduct()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecentlyViewedResponse> getRecentlyViewedProductsBySession(String sessionId, int limit) {
        log.debug("Fetching recently viewed products for session {}, limit={}", sessionId, limit);

        Pageable pageable = PageRequest.of(0, limit);
        List<ProductView> productViews = productViewRepository.findRecentlyViewedBySession(sessionId, pageable);

        log.debug("Found {} recently viewed products for session {}", productViews.size(), sessionId);

        return productViews.stream()
                .map(pv -> RecentlyViewedResponse.fromProduct(pv.getProduct()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void migrateSessionViewsToUser(String sessionId, Long userId) {
        log.info("Migrating session views to user: sessionId={}, userId={}", sessionId, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        try {
            // Get all views from this session
            List<ProductView> sessionViews = productViewRepository.findBySessionIdOrderByViewedAtDesc(sessionId);
            log.debug("Found {} session views to migrate", sessionViews.size());

            // For each session view, check if user already has a view for the same product
            for (ProductView sessionView : sessionViews) {
                Optional<ProductView> userView = productViewRepository.findByUserAndProduct(userId, sessionView.getProduct().getId());

                if (userView.isPresent()) {
                    // User already viewed this product - keep the most recent timestamp
                    ProductView existing = userView.get();
                    if (sessionView.getViewedAt().isAfter(existing.getViewedAt())) {
                        existing.setViewedAt(sessionView.getViewedAt());
                        productViewRepository.save(existing);
                    }
                    // Delete the session view (duplicate)
                    productViewRepository.delete(sessionView);
                } else {
                    // User hasn't viewed this product - migrate the session view
                    sessionView.setUser(user);
                    sessionView.setSessionId(null);
                    productViewRepository.save(sessionView);
                }
            }

            log.info("Successfully migrated {} session views to user {}", sessionViews.size(), userId);
        } catch (Exception e) {
            log.error("Error migrating session views to user: sessionId={}, userId={}", sessionId, userId, e);
            throw new RuntimeException("Failed to migrate session views", e);
        }
    }

    @Transactional
    public void cleanupOldViews() {
        log.info("Starting cleanup of old view history records");

        try {
            // Delete views older than 90 days
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
            productViewRepository.deleteViewsOlderThan(cutoffDate);

            log.info("Cleanup completed successfully");
        } catch (Exception e) {
            log.error("Error during view history cleanup", e);
            throw new RuntimeException("Failed to cleanup old views", e);
        }
    }

    @Transactional(readOnly = true)
    public Long getViewCountForUser(Long userId) {
        return productViewRepository.countByUser(userId);
    }

    @Transactional(readOnly = true)
    public Long getViewCountForSession(String sessionId) {
        return productViewRepository.countBySession(sessionId);
    }
}
