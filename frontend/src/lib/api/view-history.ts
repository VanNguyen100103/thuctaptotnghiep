/**
 * View History API Client
 * Handles product view tracking and recently viewed products
 */

import { get, post } from './client';

// ==================== TYPES ====================

export interface TrackViewRequest {
  productId: number;
  sessionId?: string;
  ipAddress?: string;
  userAgent?: string;
}

export interface RecentlyViewedProduct {
  id: number;
  name: string;
  slug: string;
  price: number;
  compareAtPrice?: number;
  discountPercentage?: number;
  imageUrl?: string;
  averageRating: number;
  reviewCount: number;
  inStock: boolean;
  viewedAt?: string;
  brand?: string;
  availableColors?: string[];
  availableSizes?: string[];
}

export interface RecentlyViewedResponse {
  success: boolean;
  data: RecentlyViewedProduct[];
  count: number;
}

export interface TrackViewResponse {
  success: boolean;
  message: string;
  productId: number;
}

export interface MigrateViewsRequest {
  sessionId: string;
}

// ==================== API FUNCTIONS ====================

/**
 * Track a product view
 * Works for both authenticated and anonymous users
 *
 * @param request - View tracking request with productId and optional sessionId
 * @returns Promise with tracking result
 */
export async function trackProductView(
  request: TrackViewRequest
): Promise<TrackViewResponse> {
  return post<TrackViewResponse>('/api/views/track', request);
}

/**
 * Get recently viewed products for authenticated user
 * Requires authentication
 *
 * @param limit - Maximum number of products to return (default: 10, max: 50)
 * @returns Promise with list of recently viewed products
 */
export async function getRecentlyViewedProducts(
  limit: number = 10
): Promise<RecentlyViewedResponse> {
  return get<RecentlyViewedResponse>(`/api/views/recently-viewed?limit=${limit}`);
}

/**
 * Get recently viewed products for anonymous session
 * No authentication required
 *
 * @param sessionId - Anonymous session ID
 * @param limit - Maximum number of products to return (default: 10, max: 50)
 * @returns Promise with list of recently viewed products
 */
export async function getRecentlyViewedBySession(
  sessionId: string,
  limit: number = 10
): Promise<RecentlyViewedResponse> {
  return get<RecentlyViewedResponse>(
    `/api/views/recently-viewed/session/${sessionId}?limit=${limit}`
  );
}

/**
 * Migrate anonymous session views to authenticated user
 * Called automatically when user logs in
 * Requires authentication
 *
 * @param sessionId - Anonymous session ID to migrate
 * @returns Promise with migration result
 */
export async function migrateSessionViews(
  sessionId: string
): Promise<{ success: boolean; message: string }> {
  return post('/api/views/migrate', { sessionId });
}

/**
 * Get view count for current user
 * Requires authentication
 *
 * @returns Promise with count of unique products viewed
 */
export async function getViewCount(): Promise<{ success: boolean; count: number }> {
  return get('/api/views/count');
}

// ==================== HELPER FUNCTIONS ====================

/**
 * Get or create anonymous session ID from localStorage
 *
 * @returns Session ID string
 */
export function getAnonymousSessionId(): string {
  const STORAGE_KEY = 'anonymous_session_id';

  // Check if session ID exists in localStorage
  let sessionId = localStorage.getItem(STORAGE_KEY);

  if (!sessionId) {
    // Generate new session ID
    sessionId = `anon-${Date.now()}-${Math.random().toString(36).substring(2, 15)}`;
    localStorage.setItem(STORAGE_KEY, sessionId);
  }

  return sessionId;
}

/**
 * Clear anonymous session ID from localStorage
 * Called when user logs in after migration
 */
export function clearAnonymousSessionId(): void {
  localStorage.removeItem('anonymous_session_id');
}

/**
 * Check if user is authenticated by checking for token
 *
 * @returns True if user has auth token
 */
export function isAuthenticated(): boolean {
  return !!localStorage.getItem('token');
}

/**
 * Track product view with automatic session handling
 * Handles both authenticated and anonymous users
 *
 * @param productId - Product ID to track
 * @returns Promise with tracking result
 */
export async function trackView(productId: number): Promise<TrackViewResponse> {
  const request: TrackViewRequest = {
    productId,
  };

  // If user is not authenticated, include session ID
  if (!isAuthenticated()) {
    request.sessionId = getAnonymousSessionId();
  }

  return trackProductView(request);
}

/**
 * Get recently viewed products with automatic user/session detection
 *
 * @param limit - Maximum number of products to return
 * @returns Promise with list of recently viewed products
 */
export async function getRecentlyViewed(
  limit: number = 10
): Promise<RecentlyViewedResponse> {
  if (isAuthenticated()) {
    // Get views for authenticated user
    return getRecentlyViewedProducts(limit);
  } else {
    // Get views for anonymous session
    const sessionId = getAnonymousSessionId();
    return getRecentlyViewedBySession(sessionId, limit);
  }
}

/**
 * Migrate views when user logs in
 * Should be called after successful login
 *
 * @returns Promise with migration result
 */
export async function migrateViewsOnLogin(): Promise<void> {
  const sessionId = localStorage.getItem('anonymous_session_id');

  if (sessionId && isAuthenticated()) {
    try {
      await migrateSessionViews(sessionId);
      // Clear session ID after successful migration
      clearAnonymousSessionId();
      console.log('Successfully migrated view history to user account');
    } catch (error) {
      console.error('Failed to migrate view history:', error);
      // Don't throw - migration failure shouldn't break login flow
    }
  }
}
