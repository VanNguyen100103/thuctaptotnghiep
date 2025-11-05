/**
 * Wishlist Types
 * Aligned with Spring Boot backend WishlistController responses
 */

import type { Product } from './product';

/**
 * Single wishlist item returned by backend
 */
export interface WishlistItem {
  id: number;
  product: Product;
  addedAt: string;
}

/**
 * Response from GET /api/wishlist
 */
export interface WishlistResponse {
  wishlistItems: WishlistItem[];
  count: number;
}

/**
 * Response from POST /api/wishlist
 */
export interface AddToWishlistResponse {
  message: string;
  wishlistItem: {
    id: number;
    product: Product;
  };
}

/**
 * Response from DELETE endpoints
 */
export interface RemoveFromWishlistResponse {
  message: string;
}

/**
 * Response from GET /api/wishlist/check/{productId}
 */
export interface CheckWishlistResponse {
  inWishlist: boolean;
}
