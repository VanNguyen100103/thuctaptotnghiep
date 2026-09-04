/** Response shape of GET /coupons/validate - same endpoint the storefront checkout uses (see storefront/checkout.models.ts#CouponValidation), duplicated here so the dashboard feature area doesn't reach into storefront's. */
export interface CouponValidation {
  valid: boolean;
  code?: string;
  description?: string;
  discountType?: 'PERCENTAGE' | 'FIXED_AMOUNT' | 'FREE_SHIPPING';
  discountValue?: number;
  discountAmount?: number;
  freeShipping?: boolean;
  message?: string;
}
