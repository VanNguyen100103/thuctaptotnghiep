/**
 * Coupon API Client - Matching Backend API
 * Handles all coupon and discount code API calls
 */

import { get, post } from './client';
import type { CouponValidation, CouponUsageResponse } from '@/types/coupon';

// ==================== AUTHENTICATED ENDPOINTS ====================

/**
 * Get available coupons for user
 * Endpoint: GET /api/coupons/available
 * Requires: Authentication
 */
export async function getAvailableCoupons(): Promise<any[]> {
  return get<any[]>('/api/coupons/available');
}

/**
 * Apply coupon to cart/order
 * Endpoint: POST /api/coupons/apply
 * Requires: Authentication
 */
export async function applyCoupon(code: string): Promise<CouponValidation> {
  return post<CouponValidation>('/api/coupons/apply', { code });
}

/**
 * Validate coupon code
 * Endpoint: GET /api/coupons/validate?code=SUMMER2025&orderSubtotal=100.00
 * Requires: Authentication
 */
export async function validateCoupon(code: string, orderSubtotal: number): Promise<CouponValidation> {
  return get<CouponValidation>(`/api/coupons/validate?code=${code}&orderSubtotal=${orderSubtotal}`);
}

/**
 * Get user's coupon usage history
 * Endpoint: GET /api/coupons/my-usage
 * Requires: Authentication
 */
export async function getCouponUsageHistory(): Promise<CouponUsageResponse> {
  return get<CouponUsageResponse>('/api/coupons/my-usage');
}
