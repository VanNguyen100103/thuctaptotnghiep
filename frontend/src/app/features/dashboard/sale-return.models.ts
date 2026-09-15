import { FilterOption } from './filter-multiselect';
import { SALE_PAYMENT_METHOD_LABELS, SalePaymentMethod } from './sale.models';

/**
 * Whether the refund has actually reached the customer - mirrors backend
 * SaleReturnRefundStatus. Not the status of the return itself: the goods are
 * back and the stock is corrected the moment the receipt is written. This is
 * only about the money, which for a bank transfer leaves the till outside the
 * app entirely.
 */
export type SaleReturnRefundStatus = 'PENDING' | 'REFUNDED';

export const SALE_RETURN_REFUND_STATUS_LABELS: Record<SaleReturnRefundStatus, string> = {
  PENDING: 'Chờ chuyển tiền',
  REFUNDED: 'Đã hoàn tiền',
};

/** One line of a "Trả hàng" document - mirrors backend SaleReturnItemResponse. */
export interface SaleReturnItemDTO {
  id: number;
  /** The invoice line these units came off. */
  saleItemId: number | null;
  productId: number | null;
  productName: string;
  productSku: string;
  quantity: number;
  unitPrice: number;
  /** This line's share of the invoice line's own discount, prorated by units. */
  discountAmount: number;
  lineTotal: number;
}

export interface SaleReturnDTO {
  id: number;
  code: string;
  saleId: number | null;
  saleCode: string | null;
  customerId: number | null;
  customerCode: string | null;
  customerName: string | null;
  customerPhone: string | null;
  totalGoodsValue: number;
  /** "Giảm giá phân bổ" - the returned goods' share of the discounts the whole invoice got. */
  discountAmount: number;
  returnFee: number;
  /** "Cần trả khách" - what the shop hands back. */
  refundAmount: number;
  refundMethod: SalePaymentMethod;
  /** PENDING while the shop still owes this transfer, REFUNDED once the customer has the money. */
  refundStatus: SaleReturnRefundStatus;
  refundedAt: string | null;
  /** SePay's reference for the outgoing transfer; null when a person ticked it off instead. */
  refundReference: string | null;
  /**
   * What to type in the transfer content so SePay can settle this receipt by
   * itself. Null once it is settled - no screen should invite a second
   * transfer for money already sent.
   */
  transferContent: string | null;
  pointsRestored: number;
  pointsReverted: number;
  customerLoyaltyPoints: number | null;
  note: string | null;
  createdByUsername: string | null;
  createdAt: string;
  items: SaleReturnItemDTO[];
}

/** A row of the "Trả hàng" list: SaleReturnDTO minus the lines, which only the detail call carries. */
export type SaleReturnSummaryDTO = Omit<SaleReturnDTO, 'items'>;

export interface SaleReturnPage {
  saleReturns: SaleReturnSummaryDTO[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
  /** Summed over every return matching the filters, not just the current page. */
  totalGoodsValue: number;
  totalRefundAmount: number;
  /** Of that, what the shop has not actually sent yet - the "còn nợ khách" figure. */
  totalAwaitingTransfer: number;
}

/** One line of the invoice being returned against, with how much of it is left to return. */
export interface ReturnableLineDTO {
  saleItemId: number;
  productId: number | null;
  productName: string;
  productSku: string;
  soldQuantity: number;
  returnedQuantity: number;
  /** soldQuantity - returnedQuantity: the cap on this line's input. */
  returnableQuantity: number;
  unitPrice: number;
  discountAmount: number;
  lineTotal: number;
}

/** How the invoice was settled - the form defaults the refund to the biggest tender. */
export interface ReturnableTenderDTO {
  method: SalePaymentMethod;
  amount: number;
}

/** What the "Trả hàng" form opens on - backend ReturnableSaleResponse. */
export interface ReturnableSaleDTO {
  saleId: number;
  saleCode: string;
  saleCreatedAt: string;
  customerId: number | null;
  customerCode: string | null;
  customerName: string | null;
  customerPhone: string | null;
  customerLoyaltyPoints: number | null;
  subtotal: number;
  discountAmount: number;
  couponCode: string | null;
  couponDiscountAmount: number;
  pointsRedeemed: number;
  pointsRedeemedAmount: number;
  pointsEarned: number;
  /** What earlier returns of this invoice already moved - the cap on this one's own point figures. */
  pointsAlreadyRestored: number;
  pointsAlreadyReverted: number;
  shippingFee: number;
  otherCollectionAmount: number;
  totalAmount: number;
  payments: ReturnableTenderDTO[];
  lines: ReturnableLineDTO[];
}

export interface SaleReturnItemRequest {
  saleItemId: number;
  /** How many units of that invoice line are coming back. */
  quantity: number;
}

/**
 * The whole document in one request - there is no draft step, so unlike a
 * purchase return this body is always complete (see backend SaleReturn).
 * Carries no prices: what a return is worth is what the invoice charged.
 */
export interface CreateSaleReturnRequest {
  saleId: number;
  returnFee: number;
  refundMethod: SalePaymentMethod;
  note: string | null;
  items: SaleReturnItemRequest[];
}

/** "Phương thức hoàn tiền" - the same four tenders a sale is paid with. */
export const REFUND_METHOD_OPTIONS: FilterOption[] = (
  Object.keys(SALE_PAYMENT_METHOD_LABELS) as SalePaymentMethod[]
).map((method) => ({ value: method, label: SALE_PAYMENT_METHOD_LABELS[method] }));

/** "Trạng thái hoàn tiền" - ticking only "Chờ chuyển tiền" is the shop's "còn nợ khách" list. */
export const REFUND_STATUS_OPTIONS: FilterOption[] = (
  Object.keys(SALE_RETURN_REFUND_STATUS_LABELS) as SaleReturnRefundStatus[]
).map((status) => ({ value: status, label: SALE_RETURN_REFUND_STATUS_LABELS[status] }));

/**
 * The returned goods' share of what the whole invoice had taken off it
 * (giảm giá + coupon + điểm). Mirrors SaleReturnService's own arithmetic so
 * the form shows the number the server is about to write - the server
 * recomputes it and stays the authority either way.
 */
export function proratedInvoiceDiscount(sale: ReturnableSaleDTO, returnedGoodsValue: number): number {
  if (sale.subtotal <= 0) {
    return 0;
  }
  const invoiceDiscounts = sale.discountAmount + sale.couponDiscountAmount + sale.pointsRedeemedAmount;
  const share = (invoiceDiscounts * returnedGoodsValue) / sale.subtotal;
  return Math.min(Math.round(share * 100) / 100, returnedGoodsValue);
}

/**
 * A loyalty-point total's share of the goods coming back - the same
 * multiply-then-divide, round-down, cap-by-what-earlier-returns-took rule
 * SaleReturnService#cappedShare applies, so the form promises what the server
 * will do.
 */
export function proratedPoints(
  total: number,
  alreadyUsed: number,
  subtotal: number,
  returnedGoodsValue: number,
): number {
  if (total <= 0 || subtotal <= 0) {
    return 0;
  }
  const wanted = Math.floor((total * returnedGoodsValue) / subtotal);
  return Math.max(0, Math.min(wanted, total - alreadyUsed));
}

/** What returning `quantity` of an invoice line is worth, its own discount prorated by units. */
export function returnedLineTotal(line: ReturnableLineDTO, quantity: number): number {
  if (quantity <= 0 || line.soldQuantity <= 0) {
    return 0;
  }
  const discountShare = Math.round(((line.discountAmount * quantity) / line.soldQuantity) * 100) / 100;
  return line.unitPrice * quantity - discountShare;
}
