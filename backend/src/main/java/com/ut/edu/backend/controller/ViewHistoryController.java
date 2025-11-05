package com.ut.edu.backend.controller;

import com.ut.edu.backend.dto.RecentlyViewedResponse;
import com.ut.edu.backend.dto.TrackViewRequest;
import com.ut.edu.backend.security.UserPrincipal;
import com.ut.edu.backend.service.inter.IViewHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing product view history
 * Supports both authenticated and anonymous users
 *
 * Endpoints:
 * - POST /api/views/track - Track a product view
 * - GET /api/views/recently-viewed - Get recently viewed products (authenticated)
 * - GET /api/views/recently-viewed/session/{sessionId} - Get recently viewed products (anonymous)
 *
 * FRONTEND USAGE:
 * - Only call /api/views/track when user is logged in
 * - Frontend should check localStorage for auth token before calling track API
 */
@RestController
@RequestMapping("/views")
@RequiredArgsConstructor
@Slf4j
public class ViewHistoryController {

    private final IViewHistoryService viewHistoryService;

    /**
     * Track a product view
     * Works for both authenticated and anonymous users
     *
     * IMPORTANT: Frontend should only call this when user is logged in
     *
     * POST /api/views/track
     * Headers (if authenticated): Authorization: Bearer <JWT_TOKEN>
     * Body: {
     *   "productId": 13,
     *   "sessionId": "anon-session-abc123" // Optional for anonymous users
     * }
     */
    @PostMapping("/track")
    public ResponseEntity<?> trackProductView(
            @Valid @RequestBody TrackViewRequest request,
            HttpServletRequest httpRequest) {

        try {
            // Get authenticated user ID if available
            Long userId = getCurrentUserId();

            // Extract IP address and user agent from HTTP request
            String ipAddress = getClientIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            // Set additional tracking info
            request.setIpAddress(ipAddress);
            request.setUserAgent(userAgent);

            // Track the view
            viewHistoryService.trackProductView(request, userId);

            log.info("Product view tracked: productId={}, userId={}, sessionId={}",
                    request.getProductId(), userId, request.getSessionId());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Product view tracked successfully");
            response.put("productId", request.getProductId());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid request for tracking product view", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error tracking product view", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to track product view"
            ));
        }
    }

    /**
     * Get recently viewed products for authenticated user
     *
     * GET /api/views/recently-viewed?limit=10
     */
    @GetMapping("/recently-viewed")
    public ResponseEntity<?> getRecentlyViewedProducts(
            @RequestParam(defaultValue = "10") int limit) {

        try {
            // Get authenticated user ID
            Long userId = getCurrentUserId();

            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "success", false,
                        "error", "User must be authenticated"
                ));
            }

            // Validate limit
            if (limit < 1 || limit > 50) {
                limit = 10; // Default to 10 if invalid
            }

            List<RecentlyViewedResponse> products = viewHistoryService.getRecentlyViewedProducts(userId, limit);

            log.debug("Retrieved {} recently viewed products for user {}", products.size(), userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", products,
                    "count", products.size()
            ));

        } catch (Exception e) {
            log.error("Error retrieving recently viewed products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve recently viewed products"
            ));
        }
    }

    /**
     * Get recently viewed products for anonymous session
     *
     * GET /api/views/recently-viewed/session/{sessionId}?limit=10
     */
    @GetMapping("/recently-viewed/session/{sessionId}")
    public ResponseEntity<?> getRecentlyViewedProductsBySession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            // Validate session ID
            if (sessionId == null || sessionId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Session ID is required"
                ));
            }

            // Validate limit
            if (limit < 1 || limit > 50) {
                limit = 10; // Default to 10 if invalid
            }

            List<RecentlyViewedResponse> products = viewHistoryService.getRecentlyViewedProductsBySession(sessionId, limit);

            log.debug("Retrieved {} recently viewed products for session {}", products.size(), sessionId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", products,
                    "count", products.size()
            ));

        } catch (Exception e) {
            log.error("Error retrieving recently viewed products for session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve recently viewed products"
            ));
        }
    }

    /**
     * Migrate anonymous session views to authenticated user
     * Called automatically when user logs in
     *
     * POST /api/views/migrate
     * Body: {
     *   "sessionId": "anon-session-abc123"
     * }
     */
    @PostMapping("/migrate")
    public ResponseEntity<?> migrateSessionViews(@RequestBody Map<String, String> request) {

        try {
            Long userId = getCurrentUserId();

            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "success", false,
                        "error", "User must be authenticated"
                ));
            }

            String sessionId = request.get("sessionId");
            if (sessionId == null || sessionId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Session ID is required"
                ));
            }

            viewHistoryService.migrateSessionViewsToUser(sessionId, userId);

            log.info("Migrated session views to user: sessionId={}, userId={}", sessionId, userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Session views migrated successfully"
            ));

        } catch (Exception e) {
            log.error("Error migrating session views", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to migrate session views"
            ));
        }
    }

    /**
     * Get view count for current user
     *
     * GET /api/views/count
     */
    @GetMapping("/count")
    public ResponseEntity<?> getViewCount() {
        try {
            Long userId = getCurrentUserId();

            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "success", false,
                        "error", "User must be authenticated"
                ));
            }

            Long count = viewHistoryService.getViewCountForUser(userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "count", count
            ));

        } catch (Exception e) {
            log.error("Error getting view count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to get view count"
            ));
        }
    }

    /**
     * Helper method to get current authenticated user ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            return userPrincipal.getId();
        }

        return null;
    }

    /**
     * Helper method to extract client IP address from HTTP request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");

        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }

        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        // X-Forwarded-For can contain multiple IPs, take the first one
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        return ipAddress;
    }
}
