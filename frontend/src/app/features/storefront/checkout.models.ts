export interface ShippingAddress {
  addressLine1: string;
  addressLine2?: string;
  city: string;
  stateProvince: string;
  postalCode: string;
  country: string;
  phoneNumber?: string;
}

export interface CheckoutRequest {
  shippingAddress: ShippingAddress;
  email: string;
  couponCode?: string;
  storeSlug: string;
}

export interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  productSku: string;
  productImage: string | null;
  quantity: number;
  size: string | null;
  color: string | null;
  unitPrice: number;
  subtotal: number;
  discountAmount: number;
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  status: string;
  subtotal: number;
  shippingCost: number;
  taxAmount: number;
  discountAmount: number;
  total: number;
  shippingAddress: ShippingAddress;
  shippingEmail: string;
  trackingNumber: string | null;
  items: OrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface CheckoutResponse {
  message: string;
  order: OrderDetail;
  couponApplied: boolean;
  discountAmount: number;
}

export type PaymentMethodCode = 'PAYPAL' | 'MOMO' | 'CASH_ON_DELIVERY' | 'BANK_TRANSFER';

export interface CreatePaymentRequest {
  orderId: number;
  paymentMethod: PaymentMethodCode;
}

export interface CreatePaymentResponse {
  message: string;
  paymentId: number;
  paymentMethod: PaymentMethodCode;
  /**
   * PayPal/MoMo: an external URL to send the browser to.
   * BANK_TRANSFER (SePay): a VietQR image URL to render inline instead -
   * account/bank/amount/content are all embedded as its own query params
   * (see SePayPaymentProvider), nothing else to fetch.
   */
  redirectUrl: string;
  status: 'PENDING';
}

export interface ExecutePaymentRequest {
  paymentId: string;
  payerId: string;
}

export interface ExecutePaymentResponse {
  message: string;
  paymentId: string;
  transactionId: string;
  status: 'COMPLETED';
  orderNumber: string;
  orderStatus: 'PAID';
}

export interface PaymentDetail {
  id: number;
  orderId: number;
  orderNumber: string;
  paymentMethod: string;
  status: string;
  amount: number;
  currency: string;
  transactionId: string | null;
  paypalOrderId: string | null;
  paypalPayerEmail: string | null;
  paymentDate: string | null;
  refundAmount: number | null;
  refundDate: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** GET /payments/order/{orderId} when no Payment row exists yet. */
export interface NoPaymentYet {
  message: string;
  hasPayment: false;
}

export function isNoPaymentYet(payment: PaymentDetail | NoPaymentYet): payment is NoPaymentYet {
  return (payment as NoPaymentYet).hasPayment === false;
}

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
