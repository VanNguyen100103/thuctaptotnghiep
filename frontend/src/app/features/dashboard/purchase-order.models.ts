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
  /** Present on detail/create/update responses; absent (undefined) on list rows. */
  items?: PurchaseOrderItemDTO[];
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
