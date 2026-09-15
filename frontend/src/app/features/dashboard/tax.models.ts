/**
 * Wire types for "Thuế & Kế toán" - tờ khai 01/CNKD and its Thiết lập.
 *
 * Money arrives as `number` because the amounts a household business
 * declares are whole đồng under 10^15, well inside what a double represents
 * exactly - the same assumption every other dashboard screen already makes.
 */

/** "Ngành nghề kinh doanh" as the 01/CNKD form groups them; each carries its own rate pair. */
export type TaxActivity = 'DISTRIBUTION' | 'SERVICE' | 'PRODUCTION' | 'OTHER';

/** "Phương pháp tính thuế" - only KE_KHAI files a periodic return. */
export type TaxMethod = 'KE_KHAI' | 'KHOAN';

export type TaxPeriodType = 'QUARTER' | 'MONTH';

/**
 * "Trạng thái tờ khai". DANG_CAP_NHAT and CHUA_NOP are facts about the
 * calendar the server derives; only DA_NOP is something the shop chose.
 */
export type TaxDeclarationStatus = 'DANG_CAP_NHAT' | 'CHUA_NOP' | 'DA_NOP';

export interface TaxProfileDTO {
  businessName: string | null;
  taxCode: string | null;
  ownerName: string | null;
  businessAddress: string | null;
  taxOffice: string | null;
  taxMethod: TaxMethod;
  periodType: TaxPeriodType;
  activity: TaxActivity;
  activityLabel: string;
  /** Percent, e.g. 1 for the distribution row - not a fraction. */
  vatRate: number;
  pitRate: number;
  exemptThreshold: number;
}

export type TaxProfileRequest = Omit<TaxProfileDTO, 'activityLabel' | 'vatRate' | 'pitRate'>;

/** One row of the 01/CNKD list. */
export interface TaxDeclarationRow {
  year: number;
  periodType: TaxPeriodType;
  periodNumber: number;
  /** "Quý 1" / "Tháng 3" - the year prints under it. */
  periodLabel: string;
  startDate: string;
  endDate: string;
  /** Null while the period is still running: the list then says "Chưa đến kỳ nộp tờ khai". */
  dueDate: string | null;
  overdueDays: number;
  status: TaxDeclarationStatus;
  /** 1 = "Lần đầu", 2+ = "Bổ sung lần N". */
  declarationRound: number;
  submittedAt: string | null;
  taxableRevenue: number;
  vatAmount: number;
  pitAmount: number;
  /** "Số tiền thuế" - GTGT + TNCN, the column the list shows. */
  totalTax: number;
}

/** Everything the 01/CNKD screen draws, in one response. */
export interface TaxYearSummary {
  year: number;
  periodType: TaxPeriodType;
  taxMethod: TaxMethod;
  activity: TaxActivity;
  activityLabel: string;
  vatRate: number;
  pitRate: number;
  cumulativeVatRevenue: number;
  cumulativeVatAmount: number;
  cumulativePitRevenue: number;
  cumulativePitAmount: number;
  exemptThreshold: number;
  /** True while the year's revenue is still under the threshold - the screen says so rather than hiding figures. */
  underExemptThreshold: boolean;
  availableYears: number[];
  periods: TaxDeclarationRow[];
}

/** One of the four activity rows in section 2 of the form. */
export interface TaxActivityLine {
  activity: TaxActivity;
  label: string;
  revenue: number;
  vatRate: number;
  vatAmount: number;
  pitRate: number;
  pitAmount: number;
}

export interface TaxDeclarationDetail {
  year: number;
  periodType: TaxPeriodType;
  periodNumber: number;
  periodLabel: string;
  startDate: string;
  endDate: string;
  dueDate: string | null;
  overdueDays: number;
  status: TaxDeclarationStatus;
  declarationRound: number;
  submittedAt: string | null;
  note: string | null;
  profile: TaxProfileDTO;
  activityLines: TaxActivityLine[];
  taxableRevenue: number;
  vatAmount: number;
  pitAmount: number;
  totalTax: number;
  /** The working behind the figure: what the register took, and what the online channels took. */
  posRevenue: number;
  posCount: number;
  onlineRevenue: number;
  onlineCount: number;
  /** True once filed - the figures above are the snapshot that was sent, and the working is the live comparison. */
  frozen: boolean;
}

export interface TaxActivityOption {
  value: TaxActivity;
  label: string;
  vatRate: number;
  pitRate: number;
}

export const TAX_STATUS_LABELS: Record<TaxDeclarationStatus, string> = {
  DANG_CAP_NHAT: 'Đang cập nhật',
  CHUA_NOP: 'Chưa nộp',
  DA_NOP: 'Đã nộp',
};

/** "Lần kê khai" as the list prints it. */
export function declarationRoundLabel(round: number): string {
  return round <= 1 ? 'Lần đầu' : `Bổ sung lần ${round - 1}`;
}
