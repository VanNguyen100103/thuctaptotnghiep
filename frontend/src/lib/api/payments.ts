/**
 * Payment API Client - Matching Backend API
 * Handles all payment-related API calls (PayPal integration)
 */

import { get, post } from './client';
import type {
  Payment,
  CreatePaymentRequest,
  CreatePaymentResponse,
  ExecutePaymentRequest,
  ExecutePaymentResponse,
  RefundRequest,
  RefundResponse,
} from '@/types/payment';

// ==================== AUTHENTICATED ENDPOINTS ====================

/**
 * Create PayPal payment for an order
 * Endpoint: POST /api/payments/create
 * Requires: Authentication
 *
 * Returns approvalUrl to redirect user to PayPal
 */
export async function createPayment(data: CreatePaymentRequest): Promise<CreatePaymentResponse> {
  return post<CreatePaymentResponse>('/api/payments/create', data);
}

/**
 * Execute/Capture PayPal payment after user approval
 * Endpoint: POST /api/payments/execute
 * Requires: Authentication
 *
 * IMPORTANT: This is where stock is atomically decreased.
 * If stock is insufficient, payment is marked as FAILED and requires manual refund.
 */
export async function executePayment(data: ExecutePaymentRequest): Promise<ExecutePaymentResponse> {
  return post<ExecutePaymentResponse>('/api/payments/execute', data);
}

/**
 * Get payment details
 * Endpoint: GET /api/payments/{paymentId}
 * Requires: Authentication
 */
export async function getPayment(paymentId: number): Promise<Payment> {
  return get<Payment>(`/api/payments/${paymentId}`);
}

/**
 * Get payment by order ID
 * Endpoint: GET /api/payments/order/{orderId}
 * Requires: Authentication
 */
export async function getPaymentByOrderId(orderId: number): Promise<Payment | { message: string; hasPayment: false }> {
  return get<Payment | { message: string; hasPayment: false }>(`/api/payments/order/${orderId}`);
}

// ==================== ADMIN ONLY ENDPOINTS ====================

/**
 * Refund a payment (full or partial)
 * Endpoint: POST /api/payments/{paymentId}/refund
 * Requires: ADMIN role
 *
 * IMPORTANT:
 * - Only COMPLETED payments can be refunded
 * - Refund amount cannot exceed (payment amount - already refunded amount)
 * - Processes refund via PayPal API
 * - Updates payment status to PARTIALLY_REFUNDED or REFUNDED
 * - If fully refunded, order status becomes CANCELLED
 */
export async function refundPayment(
  paymentId: number,
  data: RefundRequest
): Promise<RefundResponse> {
  return post<RefundResponse>(`/api/payments/${paymentId}/refund`, data);
}
