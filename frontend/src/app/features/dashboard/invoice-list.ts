import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { ColumnDef, ColumnPicker } from './column-picker';
import { loadColumnPrefs, saveColumnPrefs } from './column-prefs.util';
import { InvoiceDetailPanel } from './invoice-detail-panel';
import { SalePage, SaleSummaryDTO } from './sale.models';
import { SaleService } from './sale.service';
import { TIME_PRESETS, TimeMode, TimePreset, presetRange } from './time-filter.util';

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
  imports: [DatePipe, VndCurrencyPipe, ColumnPicker, InvoiceDetailPanel],
  templateUrl: './invoice-list.html',
})
export class InvoiceList {
  private readonly saleService = inject(SaleService);

  readonly timePresets = TIME_PRESETS;

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

  /** "Giảm giá" as the receipt totals it: the invoice discount plus whatever the coupon and redeemed points took off. */
  discountOf(sale: SaleSummaryDTO): number {
    return sale.discountAmount + sale.couponDiscountAmount + sale.pointsRedeemedAmount;
  }

  readonly selectedId = signal<number | null>(null);

  toggleSelect(id: number): void {
    this.selectedId.update((current) => (current === id ? null : id));
  }

  readonly searchQuery = signal('');
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
        query: this.searchQuery().trim(),
        page: this.page(),
        size: this.pageSize(),
      })),
    ).pipe(
      switchMap(({ range, query, page, size }) =>
        toApiState<SalePage>(this.saleService.list(range.from, range.to, query, page, size)),
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

  onSearchInput(event: Event): void {
    this.searchQuery.set((event.target as HTMLInputElement).value);
    this.page.set(0);
  }

  setTimeMode(mode: TimeMode): void {
    this.timeMode.set(mode);
    this.page.set(0);
  }

  onPresetChange(event: Event): void {
    this.timePreset.set((event.target as HTMLSelectElement).value as TimePreset);
    this.timeMode.set('preset');
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
