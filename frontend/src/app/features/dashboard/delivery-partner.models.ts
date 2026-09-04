export interface IntegratedCarrier {
  code: string;
  name: string;
  totalOrders: number;
  codToCollect: number;
  codRemaining: number;
  totalShippingFee: number;
  feeRemaining: number;
}

/** Mirrors the carriers KiotViet ships integrations for out of the box - none are wired up yet (no backend counterpart), so every stat starts at 0 like the rest of this fresh store. */
export const INTEGRATED_CARRIERS: IntegratedCarrier[] = [
  { code: 'BEST', name: 'BEST Inc', totalOrders: 0, codToCollect: 0, codRemaining: 0, totalShippingFee: 0, feeRemaining: 0 },
  { code: 'EMS', name: 'EMS', totalOrders: 0, codToCollect: 0, codRemaining: 0, totalShippingFee: 0, feeRemaining: 0 },
  { code: 'GHN', name: 'Giao hàng nhanh', totalOrders: 0, codToCollect: 0, codRemaining: 0, totalShippingFee: 0, feeRemaining: 0 },
  { code: 'GHTK', name: 'Giao Hàng Tiết Kiệm', totalOrders: 0, codToCollect: 0, codRemaining: 0, totalShippingFee: 0, feeRemaining: 0 },
  { code: 'J&T', name: 'J&T', totalOrders: 0, codToCollect: 0, codRemaining: 0, totalShippingFee: 0, feeRemaining: 0 },
  { code: 'NJV', name: 'NinJaVan', totalOrders: 0, codToCollect: 0, codRemaining: 0, totalShippingFee: 0, feeRemaining: 0 },
  { code: 'SPX', name: 'SPX', totalOrders: 0, codToCollect: 0, codRemaining: 0, totalShippingFee: 0, feeRemaining: 0 },
  { code: 'VTP', name: 'Viettel Post', totalOrders: 0, codToCollect: 0, codRemaining: 0, totalShippingFee: 0, feeRemaining: 0 },
  { code: 'AHAMOVE', name: 'AhaMove', totalOrders: 0, codToCollect: 0, codRemaining: 0, totalShippingFee: 0, feeRemaining: 0 },
];
