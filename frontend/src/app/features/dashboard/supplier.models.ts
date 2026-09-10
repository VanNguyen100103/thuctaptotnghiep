export interface SupplierDTO {
  id: number;
  code: string;
  name: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  /** "Khu vực" (Tỉnh/Thành phố - Quận/Huyện) - plain text, no province/district dataset behind it. */
  region: string | null;
  /** "Phường/Xã" - plain text, same reasoning as region. */
  ward: string | null;
  /** "Nhóm nhà cung cấp" - free-text tag, same treatment as Product's "Thương hiệu". */
  groupName: string | null;
  taxCode: string | null;
  /** "Tên công ty" under "Thông tin xuất hóa đơn". */
  companyName: string | null;
  note: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  /** "Tổng mua" - what the store has bought from this supplier on completed receipts, inside the list's Thời gian range. */
  totalPurchase: number;
  /** "Nợ cần trả hiện tại" - what is still owed on those receipts. All-time, unlike totalPurchase. */
  currentDebt: number;
}

export interface SupplierRequest {
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  region?: string;
  ward?: string;
  groupName?: string;
  taxCode?: string;
  companyName?: string;
  note?: string;
}

/** KiotViet's three "Trạng thái" pills: their own default is "Đang hoạt động". */
export type SupplierStatusFilter = 'all' | 'active' | 'inactive';

/**
 * Everything the Nhà cung cấp list sends in one object rather than a dozen
 * positional arguments - same reasoning as PurchaseOrderListQuery.
 */
export interface SupplierListQuery {
  /** The search box itself - "Theo mã, tên nhà cung cấp" (also matches the phone, like the Nhập hàng form's box). */
  query: string;
  /** The two boxes behind its sliders icon. */
  phone: string;
  note: string;
  /** "Nhóm nhà cung cấp", or '' for every group. */
  group: string;
  status: SupplierStatusFilter;
  /** "Tổng mua" value range - null on either side means unbounded, not zero. */
  totalFrom: number | null;
  totalTo: number | null;
  /** "Nợ hiện tại" value range. */
  debtFrom: number | null;
  debtTo: number | null;
  /** "Thời gian" - narrows Tổng mua only; the debt column is what stands today. */
  from: string | null;
  to: string | null;
  page: number;
  size: number;
}

export interface SupplierPage {
  suppliers: SupplierDTO[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
  /** Sums across every supplier matching the filters, not just the page on screen. */
  totalPurchaseSum: number;
  totalDebtSum: number;
}
