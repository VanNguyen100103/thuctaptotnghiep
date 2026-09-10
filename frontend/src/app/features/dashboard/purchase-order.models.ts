export type PurchaseOrderStatus = 'DRAFT' | 'COMPLETED' | 'CANCELLED';

export interface PurchaseOrderItemDTO {
  id: number;
  productId: number;
  productName: string;
  productSku: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  lineTotal: number;
}

export interface PurchaseOrderDTO {
  id: number;
  code: string;
  supplierId: number | null;
  supplierCode: string | null;
  supplierName: string | null;
  status: PurchaseOrderStatus;
  totalGoodsValue: number;
  discountAmount: number;
  /** "Chi phí nhập trả NCC" - extra charge the supplier itself bills, adds to payableAmount. */
  supplierChargeAmount: number;
  /** "Tiền trả nhà cung cấp (F8)" - cash/transfer paid right now, at receipt time. */
  amountPaid: number;
  otherCosts: number;
  /** "Cần trả nhà cung cấp" - gross obligation for this delivery, before today's payment. */
  payableAmount: number;
  /** "Tính vào công nợ" = amountPaid - payableAmount - the (usually negative) remainder booked to the supplier's running debt. */
  debtAmount: number;
  note: string | null;
  createdByUsername: string | null;
  /** "Người nhập" - who clicked "Hoàn thành"; null while still DRAFT. */
  completedByUsername: string | null;
  createdAt: string;
  completedAt: string | null;
  /** "Đánh dấu" - the star column on the list. A bookmark; nothing else reads it. */
  starred: boolean;
  /** Present on detail/create/update responses; absent (undefined) on list rows. */
  items?: PurchaseOrderItemDTO[];
}

/**
 * Everything the Nhập hàng list sends in one object rather than eleven
 * positional arguments - same reasoning as SaleListQuery: a call site reading
 * `list(from, to, code, product, supplier, note, ...)` is a bug waiting for
 * the day two of them get swapped.
 */
export interface PurchaseOrderListQuery {
  statuses: PurchaseOrderStatus[];
  from: string | null;
  to: string | null;
  /** The search box itself - "Theo mã phiếu nhập". */
  code: string;
  /** The three boxes behind its sliders icon. */
  product: string;
  supplier: string;
  note: string;
  /** "Người tạo"/"Người nhập" - a username from PurchaseOrderService#people, or '' for everyone. */
  createdBy: string;
  completedBy: string;
  page: number;
  size: number;
}

export interface PurchaseOrderPage {
  purchaseOrders: PurchaseOrderDTO[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
  /** Sum of payableAmount across every row matching the current filters, not just the current page. */
  totalPayableAmount: number;
}

export interface PurchaseOrderItemRequest {
  productId: number;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
}

export interface SavePurchaseOrderRequest {
  supplierId: number | null;
  discountAmount: number;
  supplierChargeAmount: number;
  amountPaid: number;
  otherCosts: number;
  note: string;
  items: PurchaseOrderItemRequest[];
}
