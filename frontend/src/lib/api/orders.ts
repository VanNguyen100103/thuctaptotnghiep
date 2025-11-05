/**
 * Order API Client - Matching Backend API
 * Handles all order-related API calls to Spring Boot backend
 */

import { get, post, put } from './client';
import type {
  Order,
  OrderListResponse,
  CheckoutRequest,
  CheckoutResponse,
  OrderTrackingInfo,
} from '@/types/order';

// ==================== AUTHENTICATED ENDPOINTS ====================

/**
 * Get user's orders with pagination
 * Endpoint: GET /api/orders?page={page}&size={size}
 * Requires: Authentication
 */
export async function getUserOrders(page: number = 0, size: number = 10): Promise<OrderListResponse> {
  return get<OrderListResponse>(`/api/orders?page=${page}&size=${size}`);
}

/**
 * Get order by ID
 * Endpoint: GET /api/orders/{id}
 * Requires: Authentication
 */
export async function getOrderById(id: number): Promise<Order> {
  return get<Order>(`/api/orders/${id}`);
}

/**
 * Create order from cart (Checkout)
 * Endpoint: POST /api/orders/checkout
 * Requires: Authentication
 *
 * IMPORTANT: This creates order with PENDING status.
 * Stock is NOT decreased until payment is completed.
 * Cart is cleared after successful order creation.
 */
export async function checkout(data: CheckoutRequest): Promise<CheckoutResponse> {
  return post<CheckoutResponse>('/api/orders/checkout', data);
}

/**
 * Cancel order (only if status is PENDING)
 * Endpoint: PUT /api/orders/{id}/cancel
 * Requires: Authentication
 */
export async function cancelOrder(id: number): Promise<{ message: string; order: Order }> {
  return put<{ message: string; order: Order }>(`/api/orders/${id}/cancel`, {});
}

/**
 * Get order tracking info
 * Endpoint: GET /api/orders/{id}/track
 * Requires: Authentication
 */
export async function trackOrder(id: number): Promise<OrderTrackingInfo> {
  return get<OrderTrackingInfo>(`/api/orders/${id}/track`);
}
