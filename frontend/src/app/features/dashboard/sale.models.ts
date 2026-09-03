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
  note: string;
  items: SaleItemRequest[];
  payments: SalePaymentRequest[];
}
