package com.ut.edu.backend.service.inter;

import com.ut.edu.backend.dto.*;
import com.ut.edu.backend.model.Product;

import java.util.List;
import java.util.Map;

/**
 * AI Service Interface
 * Provides AI-powered features for the e-commerce platform
 */
public interface IAIService {

    

    /**
     * Get personalized product recommendations with AI explanation
     * Returns products along with AI reasoning
     */
    AIRecommendationResponse getPersonalizedRecommendationsWithExplanation(Long userId, int limit);

    /**
     * Get similar products based on a product
     * Uses product attributes and embeddings
     */
    List<Product> getSimilarProducts(Long productId, int limit);

    /**
     * Get similar products with AI explanation
     */
    AISimilarProductsResponse getSimilarProductsWithExplanation(Long productId, int limit);

    /**
     * Chatbot conversation
     * Handles customer queries about products, orders, shipping, etc.
     */
    String chatWithBot(String userMessage, Long userId);

  
    

    /**
     * Smart search for products with complex natural language queries
     * Example: "áo nam chống nước giá 500k" or "váy dự tiệc màu đỏ dưới 1 triệu"
     * Uses AI to understand multiple criteria: category, features, price, color, etc.
     */
    AISemanticSearchResponse smartSearch(String query, int limit);

    /**
     * Cluster products into categories
     * Uses AI to automatically categorize products
     */
    Map<String, List<Product>> clusterProducts(List<Product> products, int numClusters);

    /**
     * Get trending products prediction
     * Uses AI to predict which products will be trending
     */
    List<Product> predictTrendingProducts(int limit);

    /**
     * Predict trending products with AI explanation
     */
    AIRecommendationResponse predictTrendingProductsWithExplanation(int limit);

    /**
     * Generate outfit recommendations
     * Suggests complete outfits based on a product
     */
    Map<String, Object> generateOutfitRecommendations(Long productId);

    /**
     * Analyze product reviews sentiment
     * Returns sentiment score and summary
     */
    Map<String, Object> analyzeReviewsSentiment(Long productId);

    /**
     * Analyze reviews sentiment with AI explanation
     */
    AIAnalysisResponse analyzeReviewsSentimentWithExplanation(Long productId);

    /**
     * Get size recommendation for a user
     * Based on user's previous purchases and measurements
     */
    String getSizeRecommendation(Long userId, Long productId, Integer height, Integer weight);

  
    /**
     * Generate fashion styling tips
     * Provides AI-generated styling advice
     */
    String generateStylingTips(Long productId);

    /**
     * Get all product clusters
     * Returns all products grouped by clusters (categories, price ranges, etc.)
     */
    Map<String, Object> getAllProductClusters();

 

    /**
     * Get all user clusters
     * Returns all users grouped by behavior patterns (shopping preferences, spending levels, etc.)
     */
    Map<String, Object> getAllUserClusters();

    /**
     * Analyze revenue performance using AI
     * Provides insights, trends, predictions and recommendations based on revenue data
     *
     * @param period Time period to analyze (7days, 30days, 90days, year)
     * @return AI analysis with insights, trends, and recommendations
     */
    AIAnalysisResponse analyzeRevenuePerformance(String period);

    /**
     * Get all order clusters
     * Returns all orders grouped by clusters (order value, status, time-based patterns)
     * ADMIN only - sensitive business analytics
     */
    Map<String, Object> getAllOrderClusters();

}
