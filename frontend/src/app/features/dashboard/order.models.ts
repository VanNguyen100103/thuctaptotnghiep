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
  /** "Mã KH" - a walk-in's own code, or one derived from the buyer's user id. Not a stored column in the second case. */
  customerCode: string | null;
  customerName: string | null;
  customerPhone: string | null;
  customerEmail: string | null;
  /** "Người nhận" - falls back to the buyer, who is the recipient unless the order says otherwise. */
  recipientName: string | null;
  subtotal: number;
  discountAmount: number;
  /** "Thu khác" - the register's catch-all surcharge; 0 on a storefront order. */
  otherCollectionAmount: number;
  shippingCost: number;
  taxAmount: number;
  /** "Khách cần trả". */
  total: number;
  /** "Khách đã trả" - what was actually collected, net of refunds. */
  amountPaid: number;
  paymentMethod: string | null;
  paymentStatus: string | null;
  shippingAddress: string | null;
  /** Tỉnh/TP. */
  shippingProvince: string | null;
  /** Quận/Huyện. */
  shippingDistrict: string | null;
  shippingWard: string | null;
  trackingNumber: string | null;
  shippingCarrier: string | null;
  notes: string | null;
  /** "Kênh bán". */
  salesChannel: SalesChannel;
  starred: boolean;
  /** "Người tạo" - null when the customer placed the order themselves. */
  createdBy: string | null;
  /** "Thời gian giao hàng" - when the shop promised it. */
  expectedDeliveryAt: string | null;
  /** The invoice a register order was rung up as. */
  saleCode: string | null;
  saleId: number | null;
  /** Set on the sources of a "Gộp đơn" - what they were folded into. */
  mergedIntoCode: string | null;
  /** Present on the detail response; absent on list rows. */
  items?: StoreOrderItemDTO[];
}

/** Mirrors the backend SalesChannel enum - "Kênh bán". */
export type SalesChannel =
  | 'STOREFRONT'
  | 'DIRECT'
  | 'FACEBOOK'
  | 'ZALO'
  | 'SHOPEE'
  | 'LAZADA'
  | 'TIKTOK'
  | 'OTHER';

export const SALES_CHANNEL_LABELS: Record<SalesChannel, string> = {
  STOREFRONT: 'Cửa hàng online',
  DIRECT: 'Bán trực tiếp',
  FACEBOOK: 'Facebook',
  ZALO: 'Zalo',
  SHOPEE: 'Shopee',
  LAZADA: 'Lazada',
  TIKTOK: 'TikTok Shop',
  OTHER: 'Khác',
};

export const SALES_CHANNELS: SalesChannel[] = [
  'STOREFRONT',
  'DIRECT',
  'FACEBOOK',
  'ZALO',
  'SHOPEE',
  'LAZADA',
  'TIKTOK',
  'OTHER',
];

/** One Tỉnh/TP this store has shipped to, with the Quận/Huyện under it. */
export interface DeliveryArea {
  province: string;
  districts: string[];
}

/**
 * What a bulk action did. Every one of them is partial by design - a shop
 * ticks fifteen rows and asks to finish them, two are already cancelled - so
 * the answer is "these went through, these did not and here is why" rather
 * than one all-or-nothing verdict.
 */
export interface BulkOrderResult {
  updated: number;
  updatedCodes: string[];
  /** Order code to the reason it was left alone. */
  skipped: Record<string, string>;
}

export interface MergeOrdersResult {
  message: string;
  orderId: number;
  code: string;
  mergedCodes: string[];
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

/** Mirrors the backend PaymentMethod enum - what a storefront order can be paid with. */
export type OrderPaymentMethod =
  | 'PAYPAL'
  | 'CREDIT_CARD'
  | 'DEBIT_CARD'
  | 'BANK_TRANSFER'
  | 'CASH_ON_DELIVERY'
  | 'MOMO';

export const ORDER_PAYMENT_METHOD_LABELS: Record<OrderPaymentMethod, string> = {
  PAYPAL: 'PayPal',
  CREDIT_CARD: 'Thẻ tín dụng',
  DEBIT_CARD: 'Thẻ ghi nợ',
  BANK_TRANSFER: 'Chuyển khoản',
  CASH_ON_DELIVERY: 'Thu hộ (COD)',
  MOMO: 'MoMo',
};

export const ORDER_PAYMENT_METHODS: OrderPaymentMethod[] = [
  'CASH_ON_DELIVERY',
  'BANK_TRANSFER',
  'MOMO',
  'PAYPAL',
  'CREDIT_CARD',
  'DEBIT_CARD',
];

export const ORDER_PAYMENT_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Chờ thanh toán',
  PROCESSING: 'Đang xử lý',
  COMPLETED: 'Đã thanh toán',
  FAILED: 'Thất bại',
  CANCELLED: 'Đã hủy',
  REFUNDED: 'Đã hoàn tiền',
  PARTIALLY_REFUNDED: 'Hoàn tiền một phần',
};

/**
 * The statuses "Gộp đơn" accepts, matching AdminOrderController.MERGEABLE_STATUSES:
 * nothing here has taken the customer's money yet, and money cannot be moved
 * onto a different order by a list-screen button.
 */
export const MERGEABLE_ORDER_STATUSES: StoreOrderStatus[] = ['PENDING', 'PAYMENT_PENDING', 'PENDING_COD'];

/** Mirrors the backend SalePaymentMethod enum - what a register order was tendered with. */
export const SALE_TENDER_LABELS: Record<string, string> = {
  CASH: 'Tiền mặt',
  BANK_TRANSFER: 'Chuyển khoản',
  CARD: 'Thẻ',
  EWALLET: 'Ví',
};

/**
 * "Phương thức thanh toán" as this screen offers it. The list spans both
 * halves of the shop, because "Đặt hàng" does too: a storefront order settles
 * through a gateway, a register one through a till tender. BANK_TRANSFER is
 * one entry rather than two - it is the same answer to the customer's
 * question, and the backend matches it on both sides.
 */
export const ORDER_PAYMENT_FILTER_OPTIONS: { value: string; label: string }[] = [
  { value: 'CASH', label: 'Tiền mặt' },
  { value: 'BANK_TRANSFER', label: 'Chuyển khoản' },
  { value: 'CARD', label: 'Thẻ' },
  { value: 'EWALLET', label: 'Ví' },
  { value: 'CASH_ON_DELIVERY', label: 'Thu hộ (COD)' },
  { value: 'MOMO', label: 'MoMo' },
  { value: 'PAYPAL', label: 'PayPal' },
  { value: 'CREDIT_CARD', label: 'Thẻ tín dụng' },
  { value: 'DEBIT_CARD', label: 'Thẻ ghi nợ' },
];
