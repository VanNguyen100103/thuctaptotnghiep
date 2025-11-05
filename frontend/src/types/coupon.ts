/**
 * Coupon Types - Matching Backend API
 */

export type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT' | 'FREE_SHIPPING';

export interface CouponValidation {
  valid: boolean;
  code?: string;
  description?: string;
  discountType?: DiscountType;
  discountValue?: number;
  discountAmount?: number;
  freeShipping?: boolean;
  message: string;
}

export interface CouponUsage {
  code: string;
  description: string;
  discountType: DiscountType;
  discountValue: number;
  usedAt: string;
  orderNumber: string;
  orderId: number;
  canUseAgain: boolean;
  maxUsagePerUser?: number;
  timesUsed: number;
}

export interface CouponUsageResponse {
  usedCoupons: CouponUsage[];
  totalUsed: number;
}
