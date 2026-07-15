package com.ut.edu.backend.ai;

import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.Role;
import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.order.Order;


import com.ut.edu.backend.product.ProductRepository;
import com.ut.edu.backend.security.UserPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;


import java.util.Map;

/**
 * AI Controller
 * Provides AI-powered features for the frontend
 *
 * ENABLED: Using mock AI service (no ChatClient required)
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AIController {

    private final AIService aiService;
    private final ProductRepository productRepository;

    /**
     * Get personalized product recommendations
     * GET /api/ai/recommendations?limit=10
     * SECURITY: userId is extracted from JWT token (no IDOR vulnerability)
     */
    

    /**
     * Get personalized product recommendations with AI explanation
     * GET /api/ai/recommendations-explained?limit=10
     * SECURITY: userId is extracted from JWT token (no IDOR vulnerability)
     * Returns: products + AI explanation + prompt
     */
    @GetMapping("/recommendations")
    public ResponseEntity<AIRecommendationResponse> getRecommendationsWithExplanation(
            @RequestParam(defaultValue = "10") int limit) {

        // Get userId from JWT token instead of request parameter (prevent IDOR)
        Long userId = getCurrentUserId();

        log.info("Getting AI recommendations with explanation for authenticated user: {}", userId);
        AIRecommendationResponse response = aiService.getPersonalizedRecommendationsWithExplanation(userId, limit);
        return ResponseEntity.ok(response);
    }

 
 

    /**
     * Chatbot conversation
     * POST /api/ai/chat
     * SECURITY: userId is extracted from JWT token (no IDOR vulnerability)
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");

        // Get userId from JWT token instead of request body (prevent IDOR)
        Long userId = getCurrentUserId();

        log.info("Chatbot request from authenticated user {}: {}", userId, message);

        String response = aiService.chatWithBot(message, userId);
        return ResponseEntity.ok(Map.of(
                "message", message,
                "response", response
        ));
    }

  
 

    /**
     * Smart search - AI-powered intelligent search with complex queries
     * Example: "áo nam chống nước giá 500k" or "váy dự tiệc màu đỏ dưới 1 triệu"
     * GET /api/ai/smart-search?q=áo nam chống nước giá 500k&limit=20
     */
    @GetMapping("/smart-search")
    public ResponseEntity<AISemanticSearchResponse> smartSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("Smart search: {}", q);
        AISemanticSearchResponse response = aiService.smartSearch(q, limit);
        return ResponseEntity.ok(response);
    }

   

    /**
     * Generate outfit recommendations
     * GET /api/ai/outfit/{productId}
     */
    @GetMapping("/outfit/{productId}")
    public ResponseEntity<Map<String, Object>> getOutfitRecommendations(
            @PathVariable Long productId) {

        log.info("Generating outfit recommendations for product: {}", productId);
        Map<String, Object> outfit = aiService.generateOutfitRecommendations(productId);
        return ResponseEntity.ok(outfit);
    }

  

    /**
     * Get size recommendation with user measurements
     * GET /api/ai/size-recommendation?productId=5&height=170&weight=65
     * SECURITY: userId is extracted from JWT token (no IDOR vulnerability)
     * @param height - chiều cao (cm), bắt buộc
     * @param weight - cân nặng (kg), bắt buộc
     */
    @GetMapping("/size-recommendation")
    public ResponseEntity<Map<String, Object>> getSizeRecommendation(
            @RequestParam Long productId,
            @RequestParam(required = true) Integer height,
            @RequestParam(required = true) Integer weight) {

        // Validate measurements
        if (height == null || height < 100 || height > 250) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Chiều cao không hợp lệ. Vui lòng nhập chiều cao từ 100-250 cm"
            ));
        }
        if (weight == null || weight < 30 || weight > 200) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Cân nặng không hợp lệ. Vui lòng nhập cân nặng từ 30-200 kg"
            ));
        }

        // Get userId from JWT token instead of request parameter (prevent IDOR)
        Long userId = getCurrentUserId();

        log.info("Getting size recommendation for authenticated user {} and product {} (height: {}cm, weight: {}kg)",
                userId, productId, height, weight);
        String recommendation = aiService.getSizeRecommendation(userId, productId, height, weight);
        return ResponseEntity.ok(Map.of(
                "userId", userId.toString(),
                "productId", productId.toString(),
                "height", height,
                "weight", weight,
                "recommendation", recommendation
        ));
    }

    /**
     * Generate styling tips
     * GET /api/ai/styling-tips/{productId}
     */
    @GetMapping("/styling-tips/{productId}")
    public ResponseEntity<Map<String, String>> getStylingTips(@PathVariable Long productId) {
        log.info("Generating styling tips for product: {}", productId);
        String tips = aiService.generateStylingTips(productId);
        return ResponseEntity.ok(Map.of(
                "productId", productId.toString(),
                "tips", tips
        ));
    }

    /**
     * Get all product clusters (ADMIN only)
     * GET /api/ai/clusters/products
     *
     * Returns all products grouped by:
     * - Category
     * - Price range (Dưới 200k, 200k-500k, 500k-1M, Trên 1M)
     * - Brand
     *
     * SECURITY: Only ADMIN can view product clustering analytics
     */
    @GetMapping("/clusters/products")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllProductClusters() {
        log.info("Admin: Getting all product clusters");
        Map<String, Object> clusters = aiService.getAllProductClusters();
        return ResponseEntity.ok(clusters);
    }

  

    /**
     * Get all user clusters (ADMIN only)
     * GET /api/ai/clusters/users
     *
     * Returns all users grouped by:
     * - Role (ADMIN, USER, SELLER)
     * - Status (Active/Inactive)
     * - Experience (New, Recent, Loyal, Veteran based on registration time)
     *
     * SECURITY: Only ADMIN can view user clustering analytics (sensitive data)
     */
    @GetMapping("/clusters/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllUserClusters() {
        log.info("Admin: Getting all user clusters");
        Map<String, Object> clusters = aiService.getAllUserClusters();
        return ResponseEntity.ok(clusters);
    }

    /**
     * Get all order clusters (ADMIN only)
     * GET /api/ai/clusters/orders
     *
     * Returns all orders grouped by:
     * - Order Value (Low, Medium, High, Premium)
     * - Status (PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED)
     * - Time-based patterns
     *
     * SECURITY: Only ADMIN can view order clustering analytics (sensitive business data)
     */
    @GetMapping("/clusters/orders")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllOrderClusters() {
        log.info("Admin: Getting all order clusters");
        Map<String, Object> clusters = aiService.getAllOrderClusters();
        return ResponseEntity.ok(clusters);
    }


    /**
     * Health check for AI services
     * GET /api/ai/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "AI Service",
                "features", "recommendations,chatbot,semantic-search,trending,styling,clustering"
        ));
    }

    /**
     * Helper method to get current authenticated user ID from JWT token
     * Prevents IDOR vulnerabilities by extracting userId from SecurityContext
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated");
        }

        // Extract userId from UserPrincipal (populated by JWT token)
        Object principal = authentication.getPrincipal();

        if (principal instanceof UserPrincipal) {
            UserPrincipal userPrincipal = (UserPrincipal) principal;
            return userPrincipal.getId();
        }

        throw new IllegalStateException("Cannot determine user ID from authentication principal");
    }

    // ==================== NEW ENDPOINTS WITH AI EXPLANATION ====================

    @GetMapping("/similar/{productId}")
    public ResponseEntity<AISimilarProductsResponse> getSimilarProductsExplained(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "5") int limit) {
        log.info("Getting similar products with explanation for: {}", productId);
        return ResponseEntity.ok(aiService.getSimilarProductsWithExplanation(productId, limit));
    }


    @GetMapping("/trending")
    public ResponseEntity<AIRecommendationResponse> getTrendingExplained(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting trending products with explanation");
        return ResponseEntity.ok(aiService.predictTrendingProductsWithExplanation(limit));
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/sentiment/{productId}")
    public ResponseEntity<AIAnalysisResponse> getSentimentExplained(@PathVariable Long productId) {
        log.info("Getting sentiment analysis with explanation for: {}", productId);
        return ResponseEntity.ok(aiService.analyzeReviewsSentimentWithExplanation(productId));
    }

    

    
}
