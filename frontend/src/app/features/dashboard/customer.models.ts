export interface CustomerDTO {
  id: number;
  code: string;
  name: string;
  phone: string | null;
  email: string | null;
  dateOfBirth: string | null;
  /** "Nam" / "Nữ" - free text, same treatment as Product's "Giới tính". */
  gender: string | null;
  address: string | null;
  /** "Khu vực" (Tỉnh/Thành phố - Quận/Huyện) - plain text, no province/district dataset behind it. */
  region: string | null;
  /** "Phường/Xã" - plain text, same reasoning as region. */
  ward: string | null;
  /** "Nhóm khách hàng" - free-text tag, same treatment as Supplier's "Nhóm nhà cung cấp". */
  groupName: string | null;
  note: string | null;
  active: boolean;
  /** "Điểm" - loyalty point balance, redeemable 1 point = 1,000đ at POS checkout (see sale.models.ts). */
  loyaltyPoints: number;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerRequest {
  name: string;
  phone?: string;
  email?: string;
  dateOfBirth?: string;
  gender?: string;
  address?: string;
  region?: string;
  ward?: string;
  groupName?: string;
  note?: string;
}
