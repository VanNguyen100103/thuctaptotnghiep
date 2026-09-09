/** Mirrors the backend OrderStatus enum. */
export type StoreOrderStatus =
  | 'PENDING'
  | 'PAYMENT_PENDING'
  | 'PENDING_COD'
  | 'PAID'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED'
  | 'FAILED';

export interface StoreOrderItemDTO {
  id: number;
  productId: number | null;
  productName: string;
  productSku: string | null;
  /** The variant the customer picked, already joined for display; null when the line has no variant. */
  variantLabel: string | null;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  lineTotal: number;
}

export interface StoreOrderDTO {
  id: number;
  /** "Mã đặt hàng". */
  code: string;
  status: StoreOrderStatus;
  createdAt: string;
  /** "Mã KH" - derived from the buyer's user id by the backend, not a stored column. */
  customerCode: string | null;
  customerName: string | null;
  customerPhone: string | null;
  customerEmail: string | null;
  subtotal: number;
  discountAmount: number;
  shippingCost: number;
  taxAmount: number;
  /** "Khách cần trả". */
  total: number;
  /** "Khách đã trả" - settled payments only, net of refunds. */
  amountPaid: number;
  paymentMethod: string | null;
  paymentStatus: string | null;
  shippingAddress: string | null;
  trackingNumber: string | null;
  shippingCarrier: string | null;
  notes: string | null;
  /** Present on the detail response; absent on list rows. */
  items?: StoreOrderItemDTO[];
}

export interface StoreOrderPage {
  orders: StoreOrderDTO[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
  /** Summed across every order matching the filters, not just the current page. */
  totalAmount: number;
  totalPaid: number;
}

export interface AllowedTransitions {
  orderId: number;
  currentStatus: StoreOrderStatus;
  allowedTransitions: StoreOrderStatus[];
  isTerminalStatus: boolean;
}

export const ORDER_STATUS_LABELS: Record<StoreOrderStatus, string> = {
  PENDING: 'Chờ xác nhận',
  PAYMENT_PENDING: 'Chờ thanh toán',
  PENDING_COD: 'COD chờ giao',
  PAID: 'Đã thanh toán',
  PROCESSING: 'Đang xử lý',
  SHIPPED: 'Đang giao hàng',
  DELIVERED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
  REFUNDED: 'Đã hoàn tiền',
  FAILED: 'Thất bại',
};

/** Every status, in lifecycle order - the sidebar lists them as checkboxes the way KiotViet lists its own. */
export const ORDER_STATUS_FILTERS: StoreOrderStatus[] = [
  'PENDING',
  'PAYMENT_PENDING',
  'PENDING_COD',
  'PAID',
  'PROCESSING',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
  'REFUNDED',
  'FAILED',
];

/** Ticked on arrival: the orders still in play. Must stay in step with AdminOrderController.DEFAULT_STATUSES. */
export const DEFAULT_ORDER_STATUSES: StoreOrderStatus[] = [
  'PENDING',
  'PAYMENT_PENDING',
  'PENDING_COD',
  'PAID',
  'PROCESSING',
  'SHIPPED',
  'DELIVERED',
];
