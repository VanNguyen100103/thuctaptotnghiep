/**
 * Cart API Client
 * Handles all shopping cart API calls to Spring Boot backend
 */

import { get, post, put, del } from './client';
import type {
  Cart,
  AddToCartRequest,
  UpdateCartItemRequest,
  AddToCartResponse,
  UpdateCartItemResponse,
  RemoveCartItemResponse,
  ClearCartResponse
} from '@/types/cart';

// ==================== AUTHENTICATED ENDPOINTS ====================

/**
 * Get user's cart
 * Endpoint: GET /api/cart
 * Requires: Authentication
 */
export async function getCart(): Promise<Cart> {
  return get<Cart>('/api/cart');
}

/**
 * Add item to cart
 * Endpoint: POST /api/cart/items
 * Requires: Authentication
 */
export async function addToCart(data: AddToCartRequest): Promise<AddToCartResponse> {
  return post<AddToCartResponse>('/api/cart/items', data);
}

/**
 * Update cart item quantity
 * Endpoint: PUT /api/cart/items/{itemId}
 * Requires: Authentication
 */
export async function updateCartItem(itemId: number, data: UpdateCartItemRequest): Promise<UpdateCartItemResponse> {
  return put<UpdateCartItemResponse>(`/api/cart/items/${itemId}`, data);
}

/**
 * Remove item from cart
 * Endpoint: DELETE /api/cart/items/{itemId}
 * Requires: Authentication
 */
export async function removeFromCart(itemId: number): Promise<RemoveCartItemResponse> {
  return del<RemoveCartItemResponse>(`/api/cart/items/${itemId}`);
}

/**
 * Clear entire cart
 * Endpoint: DELETE /api/cart/clear
 * Requires: Authentication
 */
export async function clearCart(): Promise<ClearCartResponse> {
  return del<ClearCartResponse>('/api/cart/clear');
}

/**
 * Get cart item count
 * Endpoint: GET /api/cart/count
 * Requires: Authentication
 */
export async function getCartItemCount(): Promise<{ count: number }> {
  return get<{ count: number }>('/api/cart/count');
}
