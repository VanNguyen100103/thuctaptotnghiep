import { FilterOption } from './filter-multiselect';

/** Matches backend SalePaymentMethod - the 4 tender buttons on the register. */
export type SalePaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'CARD' | 'EWALLET';

export const SALE_PAYMENT_METHOD_LABELS: Record<SalePaymentMethod, string> = {
  CASH: 'Tiền mặt',
  BANK_TRANSFER: 'Chuyển khoản',
  CARD: 'Thẻ',
  EWALLET: 'Ví',
};

/**
 * KiotViet's "Loại hóa đơn". A POS sale is handed over the counter, so every
 * invoice this register writes is NONE; DELIVERY is here because the filter
 * is the same one KiotViet shows, and it will have rows the day a sale can be
 * shipped.
 */
export const INVOICE_TYPE_OPTIONS: FilterOption[] = [
  { value: 'NONE', label: 'Không giao hàng' },
  { value: 'DELIVERY', label: 'Giao hàng' },
];

/** "Trạng thái hóa đơn". Checkout is atomic (see backend Sale), so every invoice here is COMPLETED. */
export const INVOICE_STATUS_OPTIONS: FilterOption[] = [
  { value: 'PROCESSING', label: 'Đang xử lý' },
  { value: 'COMPLETED', label: 'Hoàn thành' },
  { value: 'UNDELIVERABLE', label: 'Không giao được' },
  { value: 'CANCELLED', label: 'Đã hủy' },
];

/** KiotViet's default: the two states an invoice passes through, not the two it ends badly in. */
export const DEFAULT_INVOICE_STATUSES = ['PROCESSING', 'COMPLETED'];

/** "Trạng thái HĐĐT" - hóa đơn điện tử. No e-invoice provider is wired up, so every invoice is NOT_ISSUED. */
export const EINVOICE_STATUS_OPTIONS: FilterOption[] = [
  { value: 'NOT_ISSUED', label: 'Chưa phát hành' },
  { value: 'PENDING_CODE', label: 'Chờ cấp mã' },
  { value: 'ISSUED', label: 'Đã phát hành' },
  { value: 'FAILED', label: 'Phát hành lỗi' },
  { value: 'CANCELLED', label: 'Đã hủy' },
];

/** "Trạng thái giao hàng" - a POS invoice has no shipment at all, so no invoice carries any of these. */
export const DELIVERY_STATUS_OPTIONS: FilterOption[] = [
  { value: 'PENDING', label: 'Chờ xử lý' },
  { value: 'PICKING', label: 'Đang lấy hàng' },
  { value: 'DELIVERING', label: 'Đang giao hàng' },
  { value: 'DELIVERED', label: 'Giao thành công' },
  { value: 'RETURNING', label: 'Đang chuyển hoàn' },
  { value: 'RETURNED', label: 'Đã chuyển hoàn' },
  { value: 'CANCELLED', label: 'Đã hủy' },
];

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

/** A row of the "Hóa đơn" list: SaleDTO minus the lines and tenders, which only the detail call carries. */
export type SaleSummaryDTO = Omit<SaleDTO, 'items' | 'payments'>;

export interface SalePage {
  sales: SaleSummaryDTO[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
  /** Summed over every invoice matching the filters, not just the current page. */
  totalAmount: number;
  totalReceived: number;
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
  /**
   * "Ban giao hang" only. Its presence is what tells the backend to raise an
   * order alongside the invoice, so a counter sale sends null rather than an
   * empty object.
   */
  delivery: SaleDeliveryRequest | null;
}

/** Who the parcel goes to and where - the delivery panel's own half of a checkout. */
export interface SaleDeliveryRequest {
  recipientName: string;
  recipientPhone: string;
  /** Street line, already joined with the "Thon/Ap" and "Khu pho" detail boxes. */
  address: string;
  provinceName: string | null;
  districtName: string | null;
  wardName: string | null;
  note: string | null;
  /** "Thu ho tien" - the courier collects at the door, so nothing has been paid yet. */
  codEnabled: boolean;
  carrierName: string | null;
  /** ISO local date-time; null when nobody promised a date. */
  expectedDeliveryAt: string | null;
}

/** What a checkout wrote. `orderId`/`orderCode` are present only for "Bán giao hàng". */
export interface CheckoutResponse {
  message: string;
  sale: SaleDTO;
  orderId?: number;
  orderCode?: string;
}
