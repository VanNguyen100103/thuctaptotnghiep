package com.ut.edu.backend.service.inter;

import com.ut.edu.backend.dto.RecentlyViewedResponse;
import com.ut.edu.backend.dto.TrackViewRequest;

import java.util.List;

/**
 * Service interface for product view history tracking
 */
public interface IViewHistoryService {

    /**
     * Track a product view for authenticated or anonymous user
     *
     * @param request View tracking request
     * @param userId Optional user ID (null for anonymous users)
     */
    void trackProductView(TrackViewRequest request, Long userId);

    /**
     * Get recently viewed products for authenticated user
     *
     * @param userId User ID
     * @param limit Maximum number of products to return
     * @return List of recently viewed products
     */
    List<RecentlyViewedResponse> getRecentlyViewedProducts(Long userId, int limit);

    /**
     * Get recently viewed products for anonymous session
     *
     * @param sessionId Session ID
     * @param limit Maximum number of products to return
     * @return List of recently viewed products
     */
    List<RecentlyViewedResponse> getRecentlyViewedProductsBySession(String sessionId, int limit);

    /**
     * Migrate anonymous session views to authenticated user
     * Called when user logs in
     *
     * @param sessionId Anonymous session ID
     * @param userId User ID after login
     */
    void migrateSessionViewsToUser(String sessionId, Long userId);

    /**
     * Clean up old view history
     * Keeps only the most recent N views per user/session
     */
    void cleanupOldViews();

    /**
     * Get total count of unique products viewed by user
     *
     * @param userId User ID
     * @return Count of unique products viewed
     */
    Long getViewCountForUser(Long userId);

    /**
     * Get total count of unique products viewed by session
     *
     * @param sessionId Session ID
     * @return Count of unique products viewed
     */
    Long getViewCountForSession(String sessionId);
}
