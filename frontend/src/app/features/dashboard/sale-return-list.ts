import { DatePipe } from '@angular/common';
import { Component, WritableSignal, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { ColumnDef, ColumnPicker } from './column-picker';
import { loadColumnPrefs, saveColumnPrefs } from './column-prefs.util';
import { exportRowsToCsv } from './csv-export.util';
import { FilterCheckboxGroup } from './filter-checkbox-group';
import { FilterMultiselect, FilterOption } from './filter-multiselect';
import { FilterSelect, SelectOption } from './filter-select';
import { SALE_PAYMENT_METHOD_LABELS } from './sale.models';
import { SaleReturnDetailPanel } from './sale-return-detail-panel';
import {
  REFUND_METHOD_OPTIONS,
  REFUND_STATUS_OPTIONS,
  SALE_RETURN_REFUND_STATUS_LABELS,
  SaleReturnPage,
  SaleReturnSummaryDTO,
} from './sale-return.models';
import { SaleReturnListQuery, SaleReturnService } from './sale-return.service';
import { SearchField, SearchPanel, SearchValues } from './search-panel';
import { TIME_PRESETS, TimeMode, TimePreset, formatIsoDate, presetRange } from './time-filter.util';

const COLUMNS: ColumnDef[] = [
  { key: 'code', label: 'Mã trả hàng' },
  { key: 'createdAt', label: 'Thời gian' },
  { key: 'saleCode', label: 'Mã hóa đơn' },
  { key: 'customerCode', label: 'Mã KH' },
  { key: 'customerName', label: 'Khách hàng' },
  { key: 'customerPhone', label: 'Điện thoại' },
  { key: 'createdBy', label: 'Người trả hàng' },
  { key: 'totalGoodsValue', label: 'Tổng tiền hàng trả lại' },
  { key: 'discount', label: 'Giảm giá phân bổ' },
  { key: 'returnFee', label: 'Phí trả hàng' },
  { key: 'refundAmount', label: 'Cần trả khách' },
  { key: 'refundMethod', label: 'PT hoàn tiền' },
  { key: 'refundStatus', label: 'Trạng thái hoàn tiền' },
  { key: 'note', label: 'Ghi chú' },
];

const DEFAULT_COLUMNS = [
  'code',
  'createdAt',
  'saleCode',
  'customerName',
  'createdBy',
  'totalGoodsValue',
  'refundAmount',
  'refundStatus',
];

const COLUMN_STORAGE_KEY = 'tryum.sale-return-list.columns';

/**
 * "Trả hàng" - the returns the counter has taken back, newest first. The Hóa
 * đơn list with the money pointing the other way, and the same kit behind it.
 *
 * Deliberately thinner than that list in two places, because a return has
 * less to say: no "Trạng thái" section (a return is final the moment it is
 * written - see backend SaleReturn) and no "Tạo mới" button, since a return
 * is always raised from the invoice it belongs to.
 */
@Component({
  selector: 'app-sale-return-list',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    VndCurrencyPipe,
    FilterCheckboxGroup,
    FilterMultiselect,
    FilterSelect,
    ColumnPicker,
    SearchPanel,
    SaleReturnDetailPanel,
  ],
  templateUrl: './sale-return-list.html',
})
export class SaleReturnList {
  private readonly route = inject(ActivatedRoute);
  private readonly saleReturnService = inject(SaleReturnService);

  readonly timePresets = TIME_PRESETS;
  readonly paymentMethodLabels = SALE_PAYMENT_METHOD_LABELS;
  readonly refundStatusLabels = SALE_RETURN_REFUND_STATUS_LABELS;

  /**
   * Bumped when a refund is ticked off inside an expanded row, so the rows
   * and the "còn nợ khách" total refetch - both are wrong the moment one
   * receipt changes state, and neither is recomputable on the client.
   */
  private readonly reloadToken = signal(0);

  onRefundSettled(): void {
    this.reloadToken.update((n) => n + 1);
  }

  /**
   * "Chi nhánh". A store is one branch here, so both states of this box list
   * the same returns - it is KiotViet's control, kept because the sidebar
   * reads wrong without it, same as on Hóa đơn.
   */
  readonly branchOptions: FilterOption[] = [{ value: 'main', label: 'Chi nhánh trung tâm' }];
  readonly branches = signal<string[]>(['main']);

  readonly refundMethodOptions = REFUND_METHOD_OPTIONS;
  readonly refundMethods = signal<string[]>([]);

  /** Ticking "Chờ chuyển tiền" alone is the "còn nợ khách" view. */
  readonly refundStatusOptions = REFUND_STATUS_OPTIONS;
  readonly refundStatuses = signal<string[]>([]);

  /** One click to the question the shop actually asks at closing time. */
  showOnlyAwaitingTransfer(): void {
    this.applyFilter(this.refundStatuses, ['PENDING']);
  }

  /** "Người trả hàng" - '' is KiotViet's "Tất cả". */
  readonly createdBy = signal('');

  /**
   * Only the people who have actually run a return, so the dropdown cannot
   * offer a name that comes back empty. A failed call leaves just "Tất cả"
   * rather than breaking the screen.
   */
  private readonly creatorUsernames = toSignal(
    this.saleReturnService.creators().pipe(
      map((response) => response.creators),
      catchError(() => of<string[]>([])),
    ),
    { initialValue: [] as string[] },
  );

  readonly creatorOptions = computed<SelectOption[]>(() => [
    { value: '', label: 'Tất cả' },
    ...this.creatorUsernames().map((username) => ({ value: username, label: username })),
  ]);

  /** Changing a filter has to send you back to page 1, or a narrower result leaves you on page 7 of 3. */
  applyFilter<T>(target: WritableSignal<T>, value: T): void {
    target.set(value);
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
   * "Xuất file" - every return the filters match, not just the page on
   * screen. Money goes out as plain numbers so Excel can sum the column.
   */
  exportCsv(): void {
    const result = this.pageState().data;
    if (!result || result.totalItems === 0 || this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.saleReturnService.list(this.listQuery(0, result.totalItems)).subscribe({
      next: (page) => {
        const keys = this.visibleColumns();
        const headers = keys.map((key) => COLUMNS.find((c) => c.key === key)?.label ?? key);
        exportRowsToCsv(
          headers,
          page.saleReturns.map((row) => keys.map((key) => this.cellValue(row, key))),
          'tra-hang.csv',
        );
        this.exporting.set(false);
      },
      error: () => this.exporting.set(false),
    });
  }

  private cellValue(row: SaleReturnSummaryDTO, key: string): string {
    switch (key) {
      case 'code':
        return row.code;
      case 'createdAt':
        return new Date(row.createdAt).toLocaleString('vi-VN');
      case 'saleCode':
        return row.saleCode ?? '';
      case 'customerCode':
        return row.customerCode ?? '';
      case 'customerName':
        return row.customerName ?? 'Khách lẻ';
      case 'customerPhone':
        return row.customerPhone ?? '';
      case 'createdBy':
        return row.createdByUsername ?? '';
      case 'totalGoodsValue':
        return String(row.totalGoodsValue);
      case 'discount':
        return String(row.discountAmount);
      case 'returnFee':
        return String(row.returnFee);
      case 'refundAmount':
        return String(row.refundAmount);
      case 'refundMethod':
        return SALE_PAYMENT_METHOD_LABELS[row.refundMethod];
      case 'refundStatus':
        return SALE_RETURN_REFUND_STATUS_LABELS[row.refundStatus];
      case 'note':
        return row.note ?? '';
      default:
        return '';
    }
  }

  /**
   * Opens on whichever return was just written, when the form navigated here
   * with it - so the cashier lands on the refund they just recorded rather
   * than having to find it.
   */
  readonly selectedId = signal<number | null>(
    this.route.snapshot.queryParamMap.get('selected')
      ? Number(this.route.snapshot.queryParamMap.get('selected'))
      : null,
  );

  toggleSelect(id: number): void {
    this.selectedId.update((current) => (current === id ? null : id));
  }

  /** The one box plus the panel behind it - the things you might know about a return. */
  readonly searchFields: SearchField[] = [
    { key: 'code', placeholder: 'Theo mã trả hàng' },
    { key: 'saleCode', placeholder: 'Theo mã hóa đơn' },
    { key: 'product', placeholder: 'Theo mã, tên hàng' },
    { key: 'customer', placeholder: 'Theo mã, tên, số điện thoại khách hàng' },
    { key: 'note', placeholder: 'Theo ghi chú' },
  ];

  readonly search = signal<SearchValues>({
    code: '',
    saleCode: '',
    product: '',
    customer: '',
    note: '',
  });

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

  readonly timeNarrowed = computed(() =>
    this.timeMode() === 'custom' ? !!(this.customFrom() || this.customTo()) : this.timePreset() !== 'all',
  );

  private readonly dateRange = computed<{ from: string | null; to: string | null }>(() =>
    this.timeMode() === 'custom'
      ? { from: this.customFrom() || null, to: this.customTo() || null }
      : presetRange(this.timePreset()),
  );

  /** Built in one place because the list and "Xuất file" have to ask the same question. */
  private listQuery(page: number, size: number): SaleReturnListQuery {
    const range = this.dateRange();
    const search = this.search();
    const text = (key: string) => (search[key] ?? '').trim();
    return {
      from: range.from,
      to: range.to,
      code: text('code'),
      saleCode: text('saleCode'),
      product: text('product'),
      customer: text('customer'),
      note: text('note'),
      refundMethods: this.refundMethods(),
      refundStatuses: this.refundStatuses(),
      createdBy: this.createdBy(),
      page,
      size,
    };
  }

  readonly pageState = toSignal(
    toObservable(
      computed(() => {
        this.reloadToken(); // read, so settling a refund refetches
        return this.listQuery(this.page(), this.pageSize());
      }),
    ).pipe(switchMap((query) => toApiState<SaleReturnPage>(this.saleReturnService.list(query)))),
    { initialValue: INITIAL_API_STATE },
  );

  readonly rangeText = computed(() => {
    const result = this.pageState().data;
    if (!result || result.totalItems === 0) {
      return null;
    }
    const from = result.currentPage * this.pageSize() + 1;
    const to = Math.min(from + result.saleReturns.length - 1, result.totalItems);
    return `${from} - ${to} trong ${result.totalItems} phiếu trả hàng`;
  });

  setTimeMode(mode: TimeMode): void {
    this.timeMode.set(mode);
    this.page.set(0);
  }

  readonly timePresetOptions = TIME_PRESETS.map((preset) => ({ value: preset.value, label: preset.label }));

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
