/** A single Tỉnh/Quận/Phường option from GHN's master-data API. id is kept as a string (GHN's ward code is alphanumeric; province/district ids are numeric but round-trip fine as text and only need parsing back to number when submitting a shipment). */
export interface GhnLocationOption {
  id: string;
  name: string;
}

export interface GhnShipmentDTO {
  id: number;
  clientOrderCode: string;
  ghnOrderCode: string | null;
  toName: string;
  toPhone: string;
  toAddress: string;
  toProvinceName: string;
  toDistrictName: string;
  toWardName: string;
  weightGrams: number;
  note: string | null;
  status: string;
  shippingFee: number;
  expectedDeliveryTime: string | null;
  createdAt: string;
}

export interface CreateGhnShipmentRequest {
  toName: string;
  toPhone: string;
  toAddress: string;
  toProvinceId: number;
  toProvinceName: string;
  toDistrictId: number;
  toDistrictName: string;
  toWardCode: string;
  toWardName: string;
  weightGrams: number;
  note?: string;
  /** Package dimensions in cm - omitted by the standalone "Tạo đơn test" modal (backend falls back to its own defaults there); the POS "Bán giao hàng" panel always sends these since they also drive its live fee quote. */
  lengthCm?: number;
  widthCm?: number;
  heightCm?: number;
}

/** Vietnamese labels for the GHN status strings this demo is likely to actually see - falls back to the raw string for anything not covered. */
export const GHN_STATUS_LABELS: Record<string, string> = {
  ready_to_pick: 'Chờ lấy hàng',
  picking: 'Đang lấy hàng',
  picked: 'Đã lấy hàng',
  storing: 'Lưu kho',
  transporting: 'Đang vận chuyển',
  sorting: 'Đang phân loại',
  delivering: 'Đang giao hàng',
  delivered: 'Đã giao hàng',
  delivery_fail: 'Giao hàng thất bại',
  waiting_to_return: 'Chờ hoàn hàng',
  return: 'Đang hoàn hàng',
  returned: 'Đã hoàn hàng',
  cancel: 'Đã hủy',
  exception: 'Ngoại lệ',
};

export function ghnStatusLabel(status: string): string {
  return GHN_STATUS_LABELS[status] ?? status;
}
