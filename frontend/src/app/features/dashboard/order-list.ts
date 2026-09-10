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
import { OrderDetailPanel } from './order-detail-panel';
import {
  DEFAULT_ORDER_STATUSES,
  ORDER_PAYMENT_METHODS,
  ORDER_PAYMENT_METHOD_LABELS,
  ORDER_STATUS_FILTERS,
  ORDER_STATUS_LABELS,
  StoreOrderDTO,
  StoreOrderPage,
  StoreOrderStatus,
} from './order.models';
import { OrderService } from './order.service';
import { SearchField, SearchPanel, SearchValues } from './search-panel';
import { TIME_PRESETS, TimeMode, TimePreset, formatIsoDate, presetRange } from './time-filter.util';

const COLUMNS: ColumnDef[] = [
  { key: 'code', label: 'Mã đặt hàng' },
  { key: 'createdAt', label: 'Thời gian' },
  { key: 'customerCode', label: 'Mã KH' },
  { key: 'customerName', label: 'Khách hàng' },
  { key: 'customerPhone', label: 'Điện thoại' },
  { key: 'subtotal', label: 'Tổng tiền hàng' },
  { key: 'discount', label: 'Giảm giá' },
  { key: 'shipping', label: 'Phí giao hàng' },
  { key: 'total', label: 'Khách cần trả' },
  { key: 'paid', label: 'Khách đã trả' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'carrier', label: 'Đối tác giao hàng' },
  { key: 'tracking', label: 'Mã vận đơn' },
  { key: 'address', label: 'Địa chỉ giao' },
];

const DEFAULT_COLUMNS = ['code', 'createdAt', 'customerCode', 'customerName', 'total', 'paid', 'status'];

const COLUMN_STORAGE_KEY = 'tryum.order-list.columns';

/**
 * "Đặt hàng" - the orders customers have placed with the store, laid out the
 * way KiotViet lays out its own order list: filters down the left, the list
 * in the middle, and a clicked row expanding into its detail right there
 * rather than navigating away.
 */
@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    VndCurrencyPipe,
    FilterMultiselect,
    FilterSelect,
    ColumnPicker,
    SearchPanel,
    OrderDetailPanel,
  ],
  templateUrl: './order-list.html',
})
export class OrderList {
  private readonly orderService = inject(OrderService);

  readonly statusLabels = ORDER_STATUS_LABELS;

  /** Drives the "Trạng thái" chip box. */
  readonly statusOptions: FilterOption[] = ORDER_STATUS_FILTERS.map((status) => ({
    value: status,
    label: ORDER_STATUS_LABELS[status],
  }));

  readonly timePresets = TIME_PRESETS;

  /** "Phương thức thanh toán" - an order with no payment record yet matches none of these, so picking any hides it. */
  readonly paymentMethodOptions: FilterOption[] = ORDER_PAYMENT_METHODS.map((method) => ({
    value: method,
    label: ORDER_PAYMENT_METHOD_LABELS[method],
  }));

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

  /** "Xuất file" - see InvoiceList.exportCsv; same rules, this list's columns. */
  exportCsv(): void {
    const result = this.pageState().data;
    if (!result || result.totalItems === 0 || this.exporting()) {
      return;
    }
    this.exporting.set(true);
    const range = this.dateRange();
    const search = this.search();
    this.orderService
      .list({
        statuses: this.statuses(),
        from: range.from,
        to: range.to,
        code: (search['code'] ?? '').trim(),
        product: (search['product'] ?? '').trim(),
        customer: (search['customer'] ?? '').trim(),
        tracking: (search['tracking'] ?? '').trim(),
        note: (search['note'] ?? '').trim(),
        paymentMethods: this.paymentMethods(),
        page: 0,
        size: result.totalItems,
      })
      .subscribe({
        next: (page) => {
          const keys = this.visibleColumns();
          const headers = keys.map((key) => COLUMNS.find((c) => c.key === key)?.label ?? key);
          exportRowsToCsv(headers, page.orders.map((order) => keys.map((key) => this.cellValue(order, key))), 'dat-hang.csv');
          this.exporting.set(false);
        },
        error: () => this.exporting.set(false),
      });
  }

  private cellValue(order: StoreOrderDTO, key: string): string {
    switch (key) {
      case 'code':
        return order.code;
      case 'createdAt':
        return new Date(order.createdAt).toLocaleString('vi-VN');
      case 'customerCode':
        return order.customerCode ?? '';
      case 'customerName':
        return order.customerName ?? 'Khách lẻ';
      case 'customerPhone':
        return order.customerPhone ?? '';
      case 'subtotal':
        return String(order.subtotal);
      case 'discount':
        return String(order.discountAmount);
      case 'shipping':
        return String(order.shippingCost);
      case 'total':
        return String(order.total);
      case 'paid':
        return String(order.amountPaid);
      case 'status':
        return ORDER_STATUS_LABELS[order.status];
      case 'carrier':
        return order.shippingCarrier ?? '';
      case 'tracking':
        return order.trackingNumber ?? '';
      case 'address':
        return order.shippingAddress ?? '';
      default:
        return '';
    }
  }

  readonly selectedId = signal<number | null>(null);

  toggleSelect(id: number): void {
    this.selectedId.update((current) => (current === id ? null : id));
  }

  /** The one box plus the panel behind it, the way KiotViet splits its search. */
  readonly searchFields: SearchField[] = [
    { key: 'code', placeholder: 'Theo mã đặt hàng' },
    { key: 'product', placeholder: 'Theo mã, tên hàng' },
    { key: 'customer', placeholder: 'Theo tên, số điện thoại khách hàng' },
    { key: 'tracking', placeholder: 'Theo mã vận đơn' },
    { key: 'note', placeholder: 'Theo ghi chú' },
  ];

  readonly search = signal<SearchValues>({ code: '', product: '', customer: '', tracking: '', note: '' });

  onSearchApplied(values: SearchValues): void {
    this.search.set(values);
    this.page.set(0);
  }

  readonly page = signal(0);
  readonly pageSize = signal(15);

  /** Ticked on arrival: the orders still in play, matching the backend's own default filter. */
  readonly statuses = signal<StoreOrderStatus[]>([...DEFAULT_ORDER_STATUSES]);

  /**
   * KiotViet opens on "Tháng này". This list opens on the whole history
   * instead - a store with a handful of orders would otherwise land on an
   * empty screen and read it as a broken page rather than a filter.
   */
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
        statuses: this.statuses(),
        range: this.dateRange(),
        search: this.search(),
        methods: this.paymentMethods(),
        page: this.page(),
        size: this.pageSize(),
        tick: this.orderService.changed(),
      })),
    ).pipe(
      switchMap(({ statuses, range, search, methods, page, size }) =>
        toApiState<StoreOrderPage>(
          this.orderService.list({
            statuses,
            from: range.from,
            to: range.to,
            code: (search['code'] ?? '').trim(),
            product: (search['product'] ?? '').trim(),
            customer: (search['customer'] ?? '').trim(),
            tracking: (search['tracking'] ?? '').trim(),
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
    const to = Math.min(from + result.orders.length - 1, result.totalItems);
    return `${from} - ${to} trong ${result.totalItems} đơn đặt hàng`;
  });

  onStatusesChanged(values: string[]): void {
    this.statuses.set(values as StoreOrderStatus[]);
    this.page.set(0);
  }

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

  /** Colour by where the order sits in its life: waiting (amber), moving (blue), done (green), stopped (grey/red). */
  statusBadgeClass(status: StoreOrderStatus): string {
    const base = 'rounded-full px-2 py-0.5 text-[11px] font-medium';
    switch (status) {
      case 'PENDING':
      case 'PAYMENT_PENDING':
      case 'PENDING_COD':
        return `${base} bg-amber-100 text-amber-700`;
      case 'PAID':
      case 'PROCESSING':
      case 'SHIPPED':
        return `${base} bg-blue-100 text-blue-700`;
      case 'DELIVERED':
        return `${base} bg-green-100 text-green-700`;
      case 'FAILED':
        return `${base} bg-red-100 text-red-700`;
      default:
        return `${base} bg-gray-100 text-gray-600`;
    }
  }
}
