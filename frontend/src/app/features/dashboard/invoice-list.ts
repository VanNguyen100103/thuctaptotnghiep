import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { ColumnDef, ColumnPicker } from './column-picker';
import { exportRowsToCsv } from './csv-export.util';
import { loadColumnPrefs, saveColumnPrefs } from './column-prefs.util';
import { FilterMultiselect, FilterOption } from './filter-multiselect';
import { FilterSelect } from './filter-select';
import { InvoiceDetailPanel } from './invoice-detail-panel';
import { SALE_PAYMENT_METHOD_LABELS, SalePage, SaleSummaryDTO } from './sale.models';
import { SearchField, SearchPanel, SearchValues } from './search-panel';
import { SaleService } from './sale.service';
import { TIME_PRESETS, TimeMode, TimePreset, formatIsoDate, presetRange } from './time-filter.util';

const COLUMNS: ColumnDef[] = [
  { key: 'code', label: 'Mã hóa đơn' },
  { key: 'createdAt', label: 'Thời gian' },
  { key: 'customerCode', label: 'Mã KH' },
  { key: 'customerName', label: 'Khách hàng' },
  { key: 'customerPhone', label: 'Điện thoại' },
  { key: 'seller', label: 'Người bán' },
  { key: 'subtotal', label: 'Tổng tiền hàng' },
  { key: 'discount', label: 'Giảm giá' },
  { key: 'otherCollection', label: 'Thu khác' },
  { key: 'total', label: 'Khách cần trả' },
  { key: 'received', label: 'Khách đã trả' },
  { key: 'coupon', label: 'Mã coupon' },
  { key: 'pointsEarned', label: 'Điểm tích lũy' },
  { key: 'note', label: 'Ghi chú' },
];

const DEFAULT_COLUMNS = [
  'code',
  'createdAt',
  'customerCode',
  'customerName',
  'seller',
  'subtotal',
  'discount',
  'total',
  'received',
];

const COLUMN_STORAGE_KEY = 'tryum.invoice-list.columns';

/**
 * "Hóa đơn" - the invoices the register has issued. This is where a POS sale
 * lands: checkout writes a Sale, so a "Bán giao hàng" invoice shows up here
 * rather than under Đặt hàng, which lists what customers ordered themselves.
 */
@Component({
  selector: 'app-invoice-list',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    VndCurrencyPipe,
    FilterMultiselect,
    FilterSelect,
    ColumnPicker,
    SearchPanel,
    InvoiceDetailPanel,
  ],
  templateUrl: './invoice-list.html',
})
export class InvoiceList {
  private readonly saleService = inject(SaleService);

  readonly timePresets = TIME_PRESETS;

  /** "Phương thức thanh toán" - matches an invoice settled with at least one of the picked tenders, since a sale can be split across several. */
  readonly paymentMethodOptions: FilterOption[] = (
    Object.keys(SALE_PAYMENT_METHOD_LABELS) as (keyof typeof SALE_PAYMENT_METHOD_LABELS)[]
  ).map((method) => ({ value: method, label: SALE_PAYMENT_METHOD_LABELS[method] }));

  readonly paymentMethods = signal<string[]>([]);

  onPaymentMethodsChanged(values: string[]): void {
    this.paymentMethods.set(values);
    this.page.set(0);
  }

  readonly columns = COLUMNS;
  readonly visibleColumns = signal<string[]>(loadColumnPrefs(COLUMN_STORAGE_KEY, DEFAULT_COLUMNS));

  isVisible(key: string): boolean {
    return this.visibleColumns().includes(key);
  }

  onColumnsChanged(keys: string[]): void {
    this.visibleColumns.set(keys);
    saveColumnPrefs(COLUMN_STORAGE_KEY, keys);
  }

  /** How many columns an expanded detail row has to span. */
  readonly columnCount = computed(() => this.visibleColumns().length);

  readonly exporting = signal(false);

  /**
   * "Xuất file" - every invoice the filters match, not just the page on
   * screen, with the columns the shop chose to see. Money goes out as plain
   * numbers so Excel can sum the column; the currency symbol would make it text.
   */
  exportCsv(): void {
    const result = this.pageState().data;
    if (!result || result.totalItems === 0 || this.exporting()) {
      return;
    }
    this.exporting.set(true);
    const range = this.dateRange();
    const search = this.search();
    this.saleService
      .list({
        from: range.from,
        to: range.to,
        code: (search['code'] ?? '').trim(),
        product: (search['product'] ?? '').trim(),
        customer: (search['customer'] ?? '').trim(),
        note: (search['note'] ?? '').trim(),
        paymentMethods: this.paymentMethods(),
        page: 0,
        size: result.totalItems,
      })
      .subscribe({
        next: (page) => {
          const keys = this.visibleColumns();
          const headers = keys.map((key) => COLUMNS.find((c) => c.key === key)?.label ?? key);
          exportRowsToCsv(headers, page.sales.map((sale) => keys.map((key) => this.cellValue(sale, key))), 'hoa-don.csv');
          this.exporting.set(false);
        },
        error: () => this.exporting.set(false),
      });
  }

  private cellValue(sale: SaleSummaryDTO, key: string): string {
    switch (key) {
      case 'code':
        return sale.code;
      case 'createdAt':
        return new Date(sale.createdAt).toLocaleString('vi-VN');
      case 'customerCode':
        return sale.customerCode ?? '';
      case 'customerName':
        return sale.customerName ?? 'Khách lẻ';
      case 'customerPhone':
        return sale.customerPhone ?? '';
      case 'seller':
        return sale.createdByUsername ?? '';
      case 'subtotal':
        return String(sale.subtotal);
      case 'discount':
        return String(this.discountOf(sale));
      case 'otherCollection':
        return String(sale.otherCollectionAmount);
      case 'total':
        return String(sale.totalAmount);
      case 'received':
        return String(sale.amountReceived);
      case 'coupon':
        return sale.couponCode ?? '';
      case 'pointsEarned':
        return String(sale.pointsEarned);
      case 'note':
        return sale.note ?? '';
      default:
        return '';
    }
  }

  /** "Giảm giá" as the receipt totals it: the invoice discount plus whatever the coupon and redeemed points took off. */
  discountOf(sale: SaleSummaryDTO): number {
    return sale.discountAmount + sale.couponDiscountAmount + sale.pointsRedeemedAmount;
  }

  readonly selectedId = signal<number | null>(null);

  toggleSelect(id: number): void {
    this.selectedId.update((current) => (current === id ? null : id));
  }

  /** The one box plus the panel behind it, the way KiotViet splits its search. */
  readonly searchFields: SearchField[] = [
    { key: 'code', placeholder: 'Theo mã hóa đơn' },
    { key: 'product', placeholder: 'Theo mã, tên hàng' },
    { key: 'customer', placeholder: 'Theo mã, tên, số điện thoại khách hàng' },
    { key: 'note', placeholder: 'Theo ghi chú' },
  ];

  readonly search = signal<SearchValues>({ code: '', product: '', customer: '', note: '' });

  onSearchApplied(values: SearchValues): void {
    this.search.set(values);
    this.page.set(0);
  }

  readonly page = signal(0);
  readonly pageSize = signal(15);

  /** Opens on the whole history rather than KiotViet's "Tháng này" - see OrderList for why. */
  readonly timeMode = signal<TimeMode>('preset');
  readonly timePreset = signal<TimePreset>('all');
  readonly customFrom = signal<string>('');
  readonly customTo = signal<string>('');

  private readonly dateRange = computed<{ from: string | null; to: string | null }>(() =>
    this.timeMode() === 'custom'
      ? { from: this.customFrom() || null, to: this.customTo() || null }
      : presetRange(this.timePreset()),
  );

  readonly pageState = toSignal(
    toObservable(
      computed(() => ({
        range: this.dateRange(),
        search: this.search(),
        methods: this.paymentMethods(),
        page: this.page(),
        size: this.pageSize(),
      })),
    ).pipe(
      switchMap(({ range, search, methods, page, size }) =>
        toApiState<SalePage>(
          this.saleService.list({
            from: range.from,
            to: range.to,
            code: (search['code'] ?? '').trim(),
            product: (search['product'] ?? '').trim(),
            customer: (search['customer'] ?? '').trim(),
            note: (search['note'] ?? '').trim(),
            paymentMethods: methods,
            page,
            size,
          }),
        ),
      ),
    ),
    { initialValue: INITIAL_API_STATE },
  );

  readonly rangeText = computed(() => {
    const result = this.pageState().data;
    if (!result || result.totalItems === 0) {
      return null;
    }
    const from = result.currentPage * this.pageSize() + 1;
    const to = Math.min(from + result.sales.length - 1, result.totalItems);
    return `${from} - ${to} trong ${result.totalItems} hóa đơn`;
  });

  setTimeMode(mode: TimeMode): void {
    this.timeMode.set(mode);
    this.page.set(0);
  }

  readonly timePresetOptions = TIME_PRESETS.map((preset) => ({ value: preset.value, label: preset.label }));

  /** The custom range lives behind one box, the way KiotViet shows "17/12/2015 - 17/12/2025" - two date inputs side by side do not fit a 208px sidebar. */
  readonly customPickerOpen = signal(false);

  readonly customRangeText = computed(() => {
    const from = this.customFrom();
    const to = this.customTo();
    if (!from && !to) {
      return null;
    }
    return `${from ? formatIsoDate(from) : '...'} - ${to ? formatIsoDate(to) : '...'}`;
  });

  toggleCustomPicker(): void {
    this.customPickerOpen.update((open) => !open);
    this.setTimeMode('custom');
  }

  setPreset(value: string): void {
    this.timePreset.set(value as TimePreset);
    this.timeMode.set('preset');
    this.customPickerOpen.set(false);
    this.page.set(0);
  }

  onCustomFromChange(event: Event): void {
    this.customFrom.set((event.target as HTMLInputElement).value);
    this.page.set(0);
  }

  onCustomToChange(event: Event): void {
    this.customTo.set((event.target as HTMLInputElement).value);
    this.page.set(0);
  }

  onPageSizeChange(event: Event): void {
    this.pageSize.set(Number((event.target as HTMLSelectElement).value));
    this.page.set(0);
  }

  firstPage(): void {
    this.page.set(0);
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
    }
  }

  nextPage(): void {
    const totalPages = this.pageState().data?.totalPages ?? 1;
    if (this.page() < totalPages - 1) {
      this.page.update((p) => p + 1);
    }
  }

  lastPage(): void {
    const totalPages = this.pageState().data?.totalPages ?? 1;
    this.page.set(Math.max(0, totalPages - 1));
  }
}
