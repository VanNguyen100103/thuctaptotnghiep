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
