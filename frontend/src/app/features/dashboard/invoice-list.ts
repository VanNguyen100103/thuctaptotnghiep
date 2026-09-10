import { DatePipe } from '@angular/common';
import { Component, WritableSignal, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { ColumnDef, ColumnPicker } from './column-picker';
import { exportRowsToCsv } from './csv-export.util';
import { loadColumnPrefs, saveColumnPrefs } from './column-prefs.util';
import { INTEGRATED_CARRIERS } from './delivery-partner.models';
import { FilterCheckboxGroup } from './filter-checkbox-group';
import { FilterMultiselect, FilterOption } from './filter-multiselect';
import { FilterSelect, SelectOption } from './filter-select';
import { InvoiceDetailPanel } from './invoice-detail-panel';
import {
  DEFAULT_INVOICE_STATUSES,
  DELIVERY_STATUS_OPTIONS,
  EINVOICE_STATUS_OPTIONS,
  INVOICE_STATUS_OPTIONS,
  INVOICE_TYPE_OPTIONS,
  SALE_PAYMENT_METHOD_LABELS,
  SalePage,
  SaleSummaryDTO,
} from './sale.models';
import { SearchField, SearchPanel, SearchValues } from './search-panel';
import { SaleListQuery, SaleService } from './sale.service';
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
    FilterCheckboxGroup,
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

  /**
   * "Chi nhánh". A store is one branch here, so both states of this box list
   * the same invoices - it is KiotViet's control, kept because the sidebar
   * reads wrong without it and because it is where a second branch will go.
   */
  readonly branchOptions: FilterOption[] = [{ value: 'main', label: 'Chi nhánh trung tâm' }];
  readonly branches = signal<string[]>(['main']);

  /**
   * KiotViet's sidebar, section for section. Four of these ask about things a
   * POS invoice has no value for yet - see SaleController for what each one
   * matches in the meantime, and sale.models.ts for where the options come
   * from. They are live filters rather than greyed-out boxes because they
   * narrow the way KiotViet's do, and will have rows to narrow the day a sale
   * can be shipped or voided.
   */
  readonly invoiceTypeOptions = INVOICE_TYPE_OPTIONS;
  readonly invoiceTypes = signal<string[]>(INVOICE_TYPE_OPTIONS.map((option) => option.value));

  readonly invoiceStatusOptions = INVOICE_STATUS_OPTIONS;
  readonly invoiceStatuses = signal<string[]>([...DEFAULT_INVOICE_STATUSES]);

  readonly einvoiceStatusOptions = EINVOICE_STATUS_OPTIONS;
  readonly einvoiceStatuses = signal<string[]>([]);

  readonly deliveryStatusOptions = DELIVERY_STATUS_OPTIONS;
  readonly deliveryStatuses = signal<string[]>([]);

  /** "Đối tác giao hàng" - the carriers KiotViet ships integrations for, same list as the Giao hàng screen. */
  readonly deliveryPartnerOptions: FilterOption[] = INTEGRATED_CARRIERS.map((carrier) => ({
    value: carrier.code,
    label: carrier.name,
  }));
  readonly deliveryPartners = signal<string[]>([]);

  /** "Phương thức thanh toán" - matches an invoice settled with at least one of the picked tenders, since a sale can be split across several. */
  readonly paymentMethodOptions: FilterOption[] = (
    Object.keys(SALE_PAYMENT_METHOD_LABELS) as (keyof typeof SALE_PAYMENT_METHOD_LABELS)[]
  ).map((method) => ({ value: method, label: SALE_PAYMENT_METHOD_LABELS[method] }));

  readonly paymentMethods = signal<string[]>([]);

  /** "Người bán" - '' is KiotViet's "Tất cả". */
  readonly seller = signal('');

  /**
   * Only the cashiers who have actually rung something up, so the dropdown
   * cannot offer a name that comes back empty. A failed call leaves just
   * "Tất cả" rather than breaking the screen - the list still loads without it.
   */
  private readonly sellerUsernames = toSignal(
    this.saleService.sellers().pipe(
      map((response) => response.sellers),
      catchError(() => of<string[]>([])),
    ),
    { initialValue: [] as string[] },
  );

  readonly sellerOptions = computed<SelectOption[]>(() => [
    { value: '', label: 'Tất cả' },
    ...this.sellerUsernames().map((username) => ({ value: username, label: username })),
  ]);

  /**
   * Every sidebar control goes through here: changing a filter has to send you
   * back to page 1, or a narrower result leaves you on page 7 of 3 looking at
   * nothing.
   */
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
    this.saleService
      .list(this.listQuery(0, result.totalItems))
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

  /**
   * The one box plus the panel behind it, the way KiotViet splits its search -
   * their eight boxes in their order. The last four ask after an e-invoice
   * number, a waybill, an order code and a line note, none of which a POS
   * invoice carries, so filling one in brings back nothing (see SaleController).
   */
  readonly searchFields: SearchField[] = [
    { key: 'code', placeholder: 'Theo mã hóa đơn' },
    { key: 'product', placeholder: 'Theo mã, tên hàng' },
    { key: 'einvoiceNumber', placeholder: 'Nhập số HĐĐT' },
    { key: 'customer', placeholder: 'Theo mã, tên, số điện thoại khách hàng' },
    { key: 'trackingCode', placeholder: 'Theo mã vận đơn' },
    { key: 'orderCode', placeholder: 'Theo mã đặt hàng' },
    { key: 'note', placeholder: 'Theo ghi chú' },
    { key: 'itemNote', placeholder: 'Theo ghi chú hàng hóa' },
  ];

  readonly search = signal<SearchValues>({
    code: '',
    product: '',
    einvoiceNumber: '',
    customer: '',
    trackingCode: '',
    orderCode: '',
    note: '',
    itemNote: '',
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

  /**
   * KiotViet marks a section with a dot while it is narrowing the list, so a
   * filter left on further down the sidebar is visible without scrolling past
   * it. A section is "narrowed" against its own default, not against empty:
   * Trạng thái hóa đơn opens with two of its four states already ticked.
   */
  readonly timeNarrowed = computed(() =>
    this.timeMode() === 'custom' ? !!(this.customFrom() || this.customTo()) : this.timePreset() !== 'all',
  );

  readonly invoiceTypesNarrowed = computed(() => this.invoiceTypes().length < INVOICE_TYPE_OPTIONS.length);

  readonly invoiceStatusesNarrowed = computed(() => {
    const selected = this.invoiceStatuses();
    return (
      selected.length !== DEFAULT_INVOICE_STATUSES.length ||
      DEFAULT_INVOICE_STATUSES.some((status) => !selected.includes(status))
    );
  });

  private readonly dateRange = computed<{ from: string | null; to: string | null }>(() =>
    this.timeMode() === 'custom'
      ? { from: this.customFrom() || null, to: this.customTo() || null }
      : presetRange(this.timePreset()),
  );

  /**
   * Everything the sidebar and the search panel currently say, as one query.
   * Built in one place because the list and "Xuất file" have to ask the same
   * question - the export is meant to be the rows on screen, all of them.
   */
  private listQuery(page: number, size: number): SaleListQuery {
    const range = this.dateRange();
    const search = this.search();
    const text = (key: string) => (search[key] ?? '').trim();
    return {
      from: range.from,
      to: range.to,
      code: text('code'),
      product: text('product'),
      customer: text('customer'),
      note: text('note'),
      einvoiceNumber: text('einvoiceNumber'),
      trackingCode: text('trackingCode'),
      orderCode: text('orderCode'),
      itemNote: text('itemNote'),
      paymentMethods: this.paymentMethods(),
      invoiceTypes: this.invoiceTypes(),
      invoiceStatuses: this.invoiceStatuses(),
      einvoiceStatuses: this.einvoiceStatuses(),
      deliveryStatuses: this.deliveryStatuses(),
      deliveryPartners: this.deliveryPartners(),
      seller: this.seller(),
      page,
      size,
    };
  }

  readonly pageState = toSignal(
    toObservable(computed(() => this.listQuery(this.page(), this.pageSize()))).pipe(
      switchMap((query) => toApiState<SalePage>(this.saleService.list(query))),
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
