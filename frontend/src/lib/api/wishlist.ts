/**
 * Wishlist API Client
 * Handles all wishlist-related API calls to Spring Boot backend
 * All POST/DELETE methods automatically use CSRF token via client.ts helpers
 */

import { get, post, del } from './client';
import type {
  WishlistResponse,
  AddToWishlistResponse,
  RemoveFromWishlistResponse,
  CheckWishlistResponse,
} from '@/types/wishlist';

// ==================== AUTHENTICATED ENDPOINTS ====================

/**
 * Get user's wishlist with all items
 * Endpoint: GET /api/wishlist
 * Requires: Authentication
 * Returns: { wishlistItems: [...], count: number }
 */
export async function getWishlist(): Promise<WishlistResponse> {
  return get<WishlistResponse>('/api/wishlist');
}

/**
 * Add product to wishlist
 * Endpoint: POST /api/wishlist
 * Requires: Authentication + CSRF Token (auto-handled by client.ts)
 * Body: { productId: number }
 */
export async function addToWishlist(productId: number): Promise<AddToWishlistResponse> {
  return post<AddToWishlistResponse>('/api/wishlist', { productId });
}

/**
 * Remove item from wishlist by wishlist ID
 * Endpoint: DELETE /api/wishlist/{wishlistId}
 * Requires: Authentication + CSRF Token (auto-handled by client.ts)
 * Includes IDOR protection - checks if current user owns this wishlist item
 */
export async function removeFromWishlistById(wishlistId: number): Promise<RemoveFromWishlistResponse> {
  return del<RemoveFromWishlistResponse>(`/api/wishlist/${wishlistId}`);
}

/**
 * Remove product from wishlist by product ID
 * Endpoint: DELETE /api/wishlist/product/{productId}
 * Requires: Authentication + CSRF Token (auto-handled by client.ts)
 * Convenience method - automatically finds and removes the wishlist item
 */
export async function removeFromWishlistByProductId(productId: number): Promise<RemoveFromWishlistResponse> {
  return del<RemoveFromWishlistResponse>(`/api/wishlist/product/${productId}`);
}

/**
 * Check if a product is in the wishlist
 * Endpoint: GET /api/wishlist/check/{productId}
 * Requires: Authentication
 * Returns: { inWishlist: boolean }
 */
export async function checkProductInWishlist(productId: number): Promise<CheckWishlistResponse> {
  return get<CheckWishlistResponse>(`/api/wishlist/check/${productId}`);
}

/**
 * Clear entire wishlist (remove all items)
 * Endpoint: DELETE /api/wishlist/clear
 * Requires: Authentication + CSRF Token (auto-handled by client.ts)
 */
export async function clearWishlist(): Promise<RemoveFromWishlistResponse> {
  return del<RemoveFromWishlistResponse>('/api/wishlist/clear');
}
