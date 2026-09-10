/**
 * One Tỉnh/Quận/Phường option from Goship's address data. `id` is a string
 * for all three levels: Goship's city and district codes are strings
 * ("100000"), its ward ids are numbers, and holding both as text means no
 * caller has to tell them apart.
 */
export interface LocationOption {
  id: string;
  name: string;
}

/**
 * One carrier's offer for a route - a row in the price comparison the
 * cashier chooses from.
 *
 * `id` is what a booking is made with. A quote and the shipment it becomes
 * are the same decision at Goship, so this id has to travel from the
 * carrier list all the way to checkout; sending a price instead would let
 * the two drift apart.
 */
export interface ShippingRate {
  id: string;
  carrierName: string;
  carrierShortName: string;
  carrierLogo?: string;
  service: string;
  /** Free text like "Dự kiến giao 2 ngày" - Goship gives a phrase, not a date. */
  expected?: string;
  /** Already includes both the carrier's charge and Goship's own service fee, which are billed together. */
  totalFee: number;
  codFee: number;
  serviceFee: number;
  /** Goship's delivery-success statistic for this carrier, so price can be weighed against reliability. */
  successPercent?: number;
}

/**
 * A booked shipment. Every field the backend can leave null is optional
 * here, not nullable: the API is configured with Jackson's non_null
 * inclusion, so an empty value is absent from the JSON rather than sent as
 * null, and `!== null` would be true for something that never happened.
 */
export interface ShipmentDTO {
  id: number;
  orderRef: string;
  goshipId?: string;
  trackingNumber?: string;
  carrierName?: string;
  carrierShortName?: string;
  service?: string;
  rateId?: string;
  toName: string;
  toPhone: string;
  toAddress: string;
  toCityName: string;
  toDistrictName: string;
  toWardName: string;
  weightGrams: number;
  codAmount: number;
  shippingFee: number;
  statusCode?: number;
  statusText?: string;
  expected?: string;
  note?: string;
  /** Always present - the column is not nullable, so it survives the API's non_null inclusion. */
  inspectionPolicy: InspectionPolicy;
  createdAt: string;
}

/**
 * What the recipient may do with the parcel before paying.
 *
 * Three states rather than a flag because the middle one is a different
 * promise: looking inside the box is not the same as trying the thing on and
 * handing it back. Goship has no field for any of it - the label below is
 * what travels, as a note to the carrier and a line on the delivery slip.
 */
export type InspectionPolicy = 'NO_INSPECTION' | 'VIEW_ONLY' | 'TRIAL_ALLOWED';

export const INSPECTION_POLICY_LABELS: Record<InspectionPolicy, string> = {
  NO_INSPECTION: 'Không cho xem hàng',
  VIEW_ONLY: 'Cho xem, không thử',
  TRIAL_ALLOWED: 'Cho thử hàng',
};

export interface RateQuoteRequest {
  toCityId: string;
  toDistrictId: string;
  weightGrams: number;
  lengthCm?: number;
  widthCm?: number;
  heightCm?: number;
  codAmount?: number;
  declaredAmount?: number;
}

export interface CreateShipmentRequest {
  /** The "Đặt hàng" row this parcel carries; absent for a booking made on its own from the Giao hàng screen. */
  orderId?: number;
  rateId: string;
  toName: string;
  toPhone: string;
  toAddress: string;
  toCityId: string;
  toCityName: string;
  toDistrictId: string;
  toDistrictName: string;
  toWardId: string;
  toWardName: string;
  weightGrams: number;
  lengthCm?: number;
  widthCm?: number;
  heightCm?: number;
  codAmount?: number;
  /** "Khai giá" - what the carrier compensates against if the parcel is lost. Absent means nothing was declared. */
  declaredAmount?: number;
  /** "Người gửi trả phí". Absent counts as true; false bills the shipping fee to the recipient at the door. */
  senderPaysShipping?: boolean;
  /** Absent falls back to NO_INSPECTION. Goship has no field for it - it reaches the courier as a note and on the printed slip. */
  inspectionPolicy?: InspectionPolicy;
  note?: string;
  /** Carried from the chosen quote purely to display - Goship's booking reply names the carrier but not the service level or the estimate. */
  service?: string;
  expected?: string;
}

/**
 * Goship's shipment status codes, mirroring the backend GoshipShipmentStatus
 * enum, which in turn is their documented table (doc.goship.io, "Shipment
 * status code").
 *
 * Goship sends a Vietnamese label alongside most statuses, so this is a
 * fallback for a payload that arrived without one - but it is also what the
 * "Trạng thái giao hàng" filter offers, and a filter cannot ask about a status
 * it has no name for. That is why the table is here in full rather than the
 * two codes it used to hold.
 */
export const GOSHIP_STATUS_LABELS: Record<number, string> = {
  900: 'Đơn mới',
  901: 'Chờ lấy hàng',
  902: 'Lấy hàng',
  903: 'Đã lấy hàng',
  904: 'Đang giao hàng',
  905: 'Giao thành công',
  906: 'Giao thất bại',
  907: 'Đang chuyển hoàn',
  908: 'Chuyển hoàn',
  909: 'Đã đối soát',
  910: 'Đã đối soát khách',
  911: 'Đã trả COD cho khách',
  912: 'Chờ thanh toán COD',
  913: 'Hoàn thành',
  914: 'Đơn hủy',
  915: 'Chậm lấy/giao',
  916: 'Giao hàng một phần',
  917: 'Thất lạc hàng',
  918: 'Đang lưu kho',
  919: 'Đang vận chuyển',
  1000: 'Đơn lỗi',
};

/**
 * The order Goship lists them in on its own shipment screen, which is the
 * order a shop reads them in. Not numeric order: 913 "Hoàn thành" sits with the
 * endings rather than between 912 and 914.
 */
export const GOSHIP_STATUS_CODES: number[] = [
  // Goship's own tab order first, so the filter reads the way their screen does.
  900, 902, 903, 904, 905, 906, 908, 912, 913, 914, 916, 917, 918, 919,
  // Then the codes their tab bar leaves out but their API still sends.
  901, 907, 909, 910, 911, 915, 1000,
];

const KNOWN_STATUS_LABELS = GOSHIP_STATUS_LABELS;

export function shipmentStatusLabel(shipment: ShipmentDTO): string {
  if (shipment.statusText) {
    return shipment.statusText;
  }
  if (shipment.statusCode === undefined) {
    return 'Chưa có trạng thái';
  }
  return KNOWN_STATUS_LABELS[shipment.statusCode] ?? `Mã ${shipment.statusCode}`;
}

/**
 * Where a store's parcels are collected. Belongs to the store, not to the
 * deployment: every shop on the platform ships from its own counter.
 *
 * The contact half is read-only - it is the store's own name, phone and
 * address, edited where a store profile is edited. Only the three Goship
 * codes are settable here, because they are the only part the app cannot
 * derive from what it already knows.
 */
export interface PickupAddress {
  name: string;
  phone: string;
  street: string;
  cityId?: string;
  districtId?: string;
  wardId?: string;
}

export interface SavePickupAddressRequest {
  cityId: string;
  districtId: string;
  wardId: string;
}
