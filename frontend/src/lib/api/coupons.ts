/**
 * Coupon API Client - Matching Backend API
 * Handles all coupon and discount code API calls
 */

import { get } from './client';
import type { CouponValidation, CouponUsageResponse } from '@/types/coupon';

// ==================== AUTHENTICATED ENDPOINTS ====================

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
