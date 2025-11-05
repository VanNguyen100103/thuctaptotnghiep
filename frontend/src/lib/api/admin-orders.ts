/**
 * Admin Orders API Client
 * Handles admin-specific order management endpoints
 */

import { get, patch, post } from './client';
import type { Order, OrderStatus } from '@/types/order';

// ==================== ADMIN ONLY ENDPOINTS ====================

/**
 * Get all orders (admin)
 * Endpoint: GET /api/admin/orders
 * Requires: ADMIN role
 */
export async function getAllOrders(params?: {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDirection?: 'ASC' | 'DESC';
  status?: OrderStatus;
}): Promise<{
  orders: Order[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
}> {
  const queryParams = new URLSearchParams();
  if (params?.page !== undefined) queryParams.append('page', params.page.toString());
  if (params?.size !== undefined) queryParams.append('size', params.size.toString());
  if (params?.sortBy) queryParams.append('sortBy', params.sortBy);
  if (params?.sortDirection) queryParams.append('sortDirection', params.sortDirection);
  if (params?.status) queryParams.append('status', params.status);

  return get(`/api/admin/orders?${queryParams.toString()}`);
}

/**
 * Get order by ID (admin)
 * Endpoint: GET /api/admin/orders/{orderId}
 * Requires: ADMIN role
 */
export async function getOrderById(orderId: number): Promise<Order> {
  return get(`/api/admin/orders/${orderId}`);
}

/**
 * Update order status
 * Endpoint: PATCH /api/admin/orders/{orderId}/status
 * Requires: ADMIN role
 */
export async function updateOrderStatus(
  orderId: number,
  status: OrderStatus
): Promise<{
  message: string;
  order: Order;
  oldStatus: OrderStatus;
  newStatus: OrderStatus;
}> {
  return patch(`/api/admin/orders/${orderId}/status`, { status });
}

/**
 * Update tracking information
 * Endpoint: PATCH /api/admin/orders/{orderId}/tracking
 * Requires: ADMIN role
 */
export async function updateTrackingNumber(
  orderId: number,
  trackingNumber: string,
  shippingCarrier?: string
): Promise<{
  message: string;
  orderId: number;
  trackingNumber: string;
  shippingCarrier: string;
}> {
  return patch(`/api/admin/orders/${orderId}/tracking`, {
    trackingNumber,
    shippingCarrier
  });
}

/**
 * Update admin notes
 * Endpoint: PATCH /api/admin/orders/{orderId}/notes
 * Requires: ADMIN role
 */
export async function updateAdminNotes(
  orderId: number,
  notes: string
): Promise<{
  message: string;
  order: Order;
}> {
  return patch(`/api/admin/orders/${orderId}/notes`, { adminNotes: notes });
}

/**
 * Cancel order (admin)
 * Endpoint: POST /api/admin/orders/{orderId}/cancel
 * Requires: ADMIN role
 */
export async function cancelOrder(
  orderId: number,
  reason?: string
): Promise<{
  message: string;
  order: Order;
}> {
  return post(`/api/admin/orders/${orderId}/cancel`, { reason });
}

/**
 * Get allowed status transitions
 * Endpoint: GET /api/admin/orders/{orderId}/allowed-transitions
 * Requires: ADMIN role
 */
export async function getAllowedTransitions(orderId: number): Promise<{
  currentStatus: OrderStatus;
  allowedTransitions: OrderStatus[];
}> {
  return get(`/api/admin/orders/${orderId}/allowed-transitions`);
}

/**
 * Search orders by order number
 * Endpoint: GET /api/admin/orders/search?query=ORD-12345
 * Requires: ADMIN role
 */
export async function searchOrders(query: string, params?: {
  page?: number;
  size?: number;
}): Promise<{
  orders: Order[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
}> {
  const queryParams = new URLSearchParams();
  queryParams.append('query', query);
  if (params?.page !== undefined) queryParams.append('page', params.page.toString());
  if (params?.size !== undefined) queryParams.append('size', params.size.toString());

  return get(`/api/admin/orders/search?${queryParams.toString()}`);
}
