/**
 * Order Types - Matching Backend API
 */

export type OrderStatus =
  | 'PENDING'
  | 'PAYMENT_PENDING'
  | 'PAID'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED'
  | 'FAILED';

export interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  productSku: string;
  productImage?: string;
  quantity: number;
  size?: string;
  color?: string;
  unitPrice: number;
  subtotal: number;
  discountAmount: number;
}

export interface ShippingAddress {
  addressLine1: string;
  addressLine2?: string;
  city: string;
  stateProvince: string;
  postalCode: string;
  country: string;
  phoneNumber?: string;
}

export interface Order {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  subtotal: number;
  shippingCost: number;
  taxAmount: number;
  discountAmount: number;
  total: number;
  items: OrderItem[];
  shippingAddressLine1: string;
  shippingAddressLine2?: string;
  shippingCity: string;
  shippingStateProvince: string;
  shippingPostalCode: string;
  shippingCountry: string;
  shippingPhoneNumber?: string;
  shippingEmail: string;
  trackingNumber?: string;
  shippingCarrier?: string;
  adminNotes?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  total: number;
  itemCount: number;
  createdAt: string;
  trackingNumber?: string;
  shippingCarrier?: string;
}

export interface CheckoutRequest {
  shippingAddress: {
    addressLine1: string;
    addressLine2?: string;
    city: string;
    stateProvince: string;
    postalCode: string;
    country: string;
    phoneNumber?: string;
  };
  email: string;
  couponCode?: string;
  paymentMethod?: string;
}

export interface CheckoutResponse {
  message: string;
  order: Order;
  couponApplied: boolean;
  discountAmount: number;
}

export interface OrderListResponse {
  orders: OrderSummary[];
  currentPage: number;
  totalPages: number;
  totalOrders: number;
}

export interface OrderTrackingInfo {
  orderNumber: string;
  status: OrderStatus;
  trackingNumber?: string;
  createdAt: string;
  updatedAt: string;
}
