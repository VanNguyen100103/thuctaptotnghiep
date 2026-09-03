import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { toApiState } from './api-state.util';
import { PurchaseOrderPage, PurchaseOrderStatus } from './purchase-order.models';
import { PurchaseOrderService } from './purchase-order.service';

type TimeFilter = 'this-month' | 'custom';

@Component({
  selector: 'app-purchase-order-list',
  standalone: true,
  imports: [RouterLink, DatePipe, VndCurrencyPipe],
  templateUrl: './purchase-order-list.html',
})
export class PurchaseOrderList {
  private readonly purchaseOrderService = inject(PurchaseOrderService);

  readonly searchQuery = signal('');
  readonly page = signal(0);
  readonly pageSize = signal(15);

  /** "Trạng thái" sidebar checkboxes - defaults match KiotViet's own list screen and the backend's default filter. */
  readonly statusDraft = signal(true);
  readonly statusCompleted = signal(true);
  readonly statusCancelled = signal(false);

  readonly timeFilter = signal<TimeFilter>('this-month');
  readonly customFrom = signal<string>('');
  readonly customTo = signal<string>('');

  private readonly selectedStatuses = computed<PurchaseOrderStatus[]>(() => {
    const statuses: PurchaseOrderStatus[] = [];
    if (this.statusDraft()) statuses.push('DRAFT');
    if (this.statusCompleted()) statuses.push('COMPLETED');
    if (this.statusCancelled()) statuses.push('CANCELLED');
    return statuses;
  });

  /** "Tháng này" = the 1st of the current month through today; "Tùy chỉnh" uses the two date pickers. */
  private readonly dateRange = computed<{ from: string | null; to: string | null }>(() => {
    if (this.timeFilter() === 'custom') {
      return { from: this.customFrom() || null, to: this.customTo() || null };
    }
    const now = new Date();
    const firstOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    return { from: firstOfMonth.toISOString().slice(0, 10), to: now.toISOString().slice(0, 10) };
  });

  readonly pageState = toSignal(
    toObservable(
      computed(() => ({
        statuses: this.selectedStatuses(),
        range: this.dateRange(),
        query: this.searchQuery().trim(),
        page: this.page(),
        size: this.pageSize(),
        tick: this.purchaseOrderService.changed(),
      })),
    ).pipe(
      switchMap(({ statuses, range, query, page, size }) =>
        toApiState<PurchaseOrderPage>(
          this.purchaseOrderService.list(statuses, range.from, range.to, query, page, size),
        ),
      ),
    ),
    { initialValue: { data: null, error: null } },
  );

  readonly rangeText = computed(() => {
    const result = this.pageState().data;
    if (!result || result.totalItems === 0) {
      return null;
    }
    const from = result.currentPage * this.pageSize() + 1;
    const to = Math.min(from + result.purchaseOrders.length - 1, result.totalItems);
    return `${from} - ${to} trong ${result.totalItems} phiếu nhập`;
  });

  onSearchInput(event: Event): void {
    this.searchQuery.set((event.target as HTMLInputElement).value);
    this.page.set(0);
  }

  toggleStatus(status: 'DRAFT' | 'COMPLETED' | 'CANCELLED'): void {
    if (status === 'DRAFT') this.statusDraft.update((v) => !v);
    if (status === 'COMPLETED') this.statusCompleted.update((v) => !v);
    if (status === 'CANCELLED') this.statusCancelled.update((v) => !v);
    this.page.set(0);
  }

  setTimeFilter(filter: TimeFilter): void {
    this.timeFilter.set(filter);
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

  nextPage(): void {
    const totalPages = this.pageState().data?.totalPages ?? 1;
    if (this.page() < totalPages - 1) {
      this.page.update((p) => p + 1);
    }
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
    }
  }

  firstPage(): void {
    this.page.set(0);
  }

  lastPage(): void {
    const totalPages = this.pageState().data?.totalPages ?? 1;
    this.page.set(Math.max(0, totalPages - 1));
  }

  statusLabel(status: PurchaseOrderStatus): string {
    switch (status) {
      case 'DRAFT':
        return 'Phiếu tạm';
      case 'COMPLETED':
        return 'Đã nhập hàng';
      case 'CANCELLED':
        return 'Đã hủy';
    }
  }
}
