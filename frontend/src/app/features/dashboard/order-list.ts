import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { FilterMultiselect, FilterOption } from './filter-multiselect';
import { OrderDetailPanel } from './order-detail-panel';
import {
  DEFAULT_ORDER_STATUSES,
  ORDER_STATUS_FILTERS,
  ORDER_STATUS_LABELS,
  StoreOrderPage,
  StoreOrderStatus,
} from './order.models';
import { OrderService } from './order.service';

import { TIME_PRESETS, TimeMode, TimePreset, presetRange } from './time-filter.util';

/**
 * "Đặt hàng" - the orders customers have placed with the store, laid out the
 * way KiotViet lays out its own order list: filters down the left, the list
 * in the middle, and a clicked row expanding into its detail right there
 * rather than navigating away.
 */
@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [DatePipe, VndCurrencyPipe, FilterMultiselect, OrderDetailPanel],
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

  readonly selectedId = signal<number | null>(null);

  toggleSelect(id: number): void {
    this.selectedId.update((current) => (current === id ? null : id));
  }

  readonly searchQuery = signal('');
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
        query: this.searchQuery().trim(),
        page: this.page(),
        size: this.pageSize(),
        tick: this.orderService.changed(),
      })),
    ).pipe(
      switchMap(({ statuses, range, query, page, size }) =>
        toApiState<StoreOrderPage>(this.orderService.list(statuses, range.from, range.to, query, page, size)),
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

  /** Colour by where the order sits in its life: waiting (amber), moving (blue), done (green), stopped (grey/red). */
  statusBadgeClass(status: StoreOrderStatus): string {
    const base = 'rounded-full px-2 py-0.5 text-xs font-medium';
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
