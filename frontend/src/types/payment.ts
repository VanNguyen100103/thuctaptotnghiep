/**
 * Payment Types - Matching Backend API
 */

export type PaymentMethod = 'PAYPAL' | 'CASH_ON_DELIVERY' | 'BANK_TRANSFER';

export type PaymentStatus =
  | 'PENDING'
  | 'COMPLETED'
  | 'FAILED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED';

export interface Payment {
  id: number;
  orderId: number;
  orderNumber: string;
  paymentMethod: PaymentMethod;
  status: PaymentStatus;
  amount: number;
  currency: string;
  transactionId?: string;
  paypalOrderId?: string;
  paypalPayerEmail?: string;
  paymentDate?: string;
  refundAmount: number;
  refundDate?: string;
  failureReason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePaymentRequest {
  orderId: number;
}

export interface CreatePaymentResponse {
  message: string;
  paymentId: number;
  paypalOrderId: string;
  approvalUrl: string;
  status: PaymentStatus;
}

export interface ExecutePaymentRequest {
  paymentId: string;
  payerId: string;
}

export interface ExecutePaymentResponse {
  message: string;
  paymentId: number;
  transactionId: string;
  status: PaymentStatus;
  orderNumber: string;
  orderStatus: string;
}

export interface RefundRequest {
  amount: number;
  reason?: string;
}

export interface RefundResponse {
  message: string;
  refundAmount: number;
  status: PaymentStatus;
  totalRefunded: number;
}
