/** Matches backend SalePaymentMethod - the 4 tender buttons on the register. */
export type SalePaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'CARD' | 'EWALLET';

export const SALE_PAYMENT_METHOD_LABELS: Record<SalePaymentMethod, string> = {
  CASH: 'Tiền mặt',
  BANK_TRANSFER: 'Chuyển khoản',
  CARD: 'Thẻ',
  EWALLET: 'Ví',
};

export interface SaleItemDTO {
  id: number;
  productId: number;
  productName: string;
  productSku: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  lineTotal: number;
}

export interface SalePaymentDTO {
  id: number;
  method: SalePaymentMethod;
  amount: number;
}

export interface SaleDTO {
  id: number;
  code: string;
  customerId: number | null;
  customerCode: string | null;
  customerName: string | null;
  customerPhone: string | null;
  subtotal: number;
  discountAmount: number;
  /** "Mã coupon" applied at checkout, re-priced server-side - null when none was used. */
  couponCode: string | null;
  couponDiscountAmount: number;
  /** "Điểm" redeemed against this sale (1 point = 1,000đ). */
  pointsRedeemed: number;
  pointsRedeemedAmount: number;
  /** "Tích điểm" earned from this sale's loyalty-eligible lines. */
  pointsEarned: number;
  /** Customer's loyalty point balance after this sale's redeem/earn - null when no customer was attached. */
  customerLoyaltyPoints: number | null;
  otherCollectionAmount: number;
  totalAmount: number;
  amountReceived: number;
  /** "Tiền thừa trả khách" - derived, not persisted. */
  changeAmount: number;
  note: string | null;
  createdByUsername: string | null;
  createdAt: string;
  items: SaleItemDTO[];
  payments: SalePaymentDTO[];
}

export interface SaleItemRequest {
  productId: number;
  quantity: number;
  unitPrice: number | null;
  discountAmount: number;
}

export interface SalePaymentRequest {
  method: SalePaymentMethod;
  amount: number;
}

export interface CreateSaleRequest {
  customerId: number | null;
  discountAmount: number;
  otherCollectionAmount: number;
  /** "Mã coupon" - the backend re-validates and re-prices this, never trusts a client-computed discount. */
  couponCode: string | null;
  /** "Điểm" the cashier chose to redeem - must not exceed the selected customer's balance. */
  pointsToRedeem: number;
  note: string;
  items: SaleItemRequest[];
  payments: SalePaymentRequest[];
}
