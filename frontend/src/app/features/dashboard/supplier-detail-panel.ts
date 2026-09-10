import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Observable, switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { PurchaseOrderDTO, PurchaseOrderPage } from './purchase-order.models';
import { PurchaseOrderService } from './purchase-order.service';
import { ActionError, toActionError } from './subscription-error.util';
import { SupplierDTO } from './supplier.models';
import { SupplierService } from './supplier.service';

/**
 * How many of a supplier's receipts the two tabs read. Enough that a demo
 * store's whole history fits, and the panel says so when it does not - the
 * ledger's running balance would otherwise start from a number nobody can
 * see.
 */
const RECEIPT_LIMIT = 100;
export type SupplierTab = 'info' | 'history' | 'debt';

/** One line of the "Nợ cần trả nhà cung cấp" tab: a receipt plus the balance standing after it. */
export interface SupplierDebtRow {
  purchaseOrder: PurchaseOrderDTO;
  /** Still owed on this receipt alone: nghĩa vụ - đã trả. */
  outstanding: number;
  /** Running total of `outstanding` up to and including this row, oldest first. */
  balance: number;
}

/**
 * The panel that opens inside the "Nhà cung cấp" list when a row is clicked,
 * with KiotViet's own three tabs. Two of them read the store's goods
 * receipts rather than anything stored on the supplier: "Lịch sử nhập hàng"
 * lists them, and "Nợ cần trả nhà cung cấp" walks the completed ones oldest
 * first to show how today's debt was arrived at.
 */
@Component({
  selector: 'app-supplier-detail-panel',
  standalone: true,
  imports: [DatePipe, VndCurrencyPipe],
  templateUrl: './supplier-detail-panel.html',
})
export class SupplierDetailPanel {
  private readonly supplierService = inject(SupplierService);
  private readonly purchaseOrderService = inject(PurchaseOrderService);

  readonly supplier = input.required<SupplierDTO>();

  readonly edit = output<SupplierDTO>();
  /** Deleted, stopped or restarted - the list refetches and closes the panel. */
  readonly changed = output<void>();
  readonly closed = output<void>();

  readonly tab = signal<SupplierTab>('info');

  setTab(tab: SupplierTab): void {
    this.tab.set(tab);
  }

  // ---- the two tabs that read goods receipts ----

  /**
   * Every receipt this supplier appears on. One fetch feeds both tabs: the
   * history lists it as it comes back (newest first), the debt ledger walks
   * the completed rows the other way round to accumulate a balance. Asked
   * for by code and then checked by id, because the list endpoint's supplier
   * box is a LIKE over mã and tên.
   */
  private readonly receiptsState = toSignal(
    toObservable(computed(() => this.supplier().code)).pipe(
      switchMap((code) =>
        toApiState<PurchaseOrderPage>(
          this.purchaseOrderService.list({
            statuses: ['DRAFT', 'COMPLETED'],
            from: null,
            to: null,
            code: '',
            product: '',
            supplier: code,
            note: '',
            createdBy: '',
            completedBy: '',
            page: 0,
            size: RECEIPT_LIMIT,
          }),
        ),
      ),
    ),
    { initialValue: INITIAL_API_STATE },
  );

  readonly receiptsError = computed(() => this.receiptsState().error);

  readonly receipts = computed<PurchaseOrderDTO[]>(() =>
    (this.receiptsState().data?.purchaseOrders ?? []).filter((po) => po.supplierId === this.supplier().id),
  );

  /** True once the supplier has more receipts than one page holds - the tabs say so rather than quietly showing a slice. */
  readonly receiptsTruncated = computed(() => (this.receiptsState().data?.totalItems ?? 0) > RECEIPT_LIMIT);

  readonly debtRows = computed<SupplierDebtRow[]>(() => {
    const completed = this.receipts()
      .filter((po) => po.status === 'COMPLETED')
      .slice()
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    let balance = 0;
    return completed.map((po) => {
      const outstanding = po.payableAmount - po.amountPaid;
      balance += outstanding;
      return { purchaseOrder: po, outstanding, balance };
    });
  });

  // ---- the buttons along the bottom ----

  readonly busy = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  dismissError(): void {
    this.actionError.set(null);
  }

  toggleActive(): void {
    const supplier = this.supplier();
    const next = !supplier.active;
    if (next === false && !window.confirm(`Ngừng hoạt động nhà cung cấp "${supplier.name}"?`)) {
      return;
    }
    this.run(this.supplierService.setActive(supplier.id, next));
  }

  requestDelete(): void {
    const supplier = this.supplier();
    if (!window.confirm(`Xóa nhà cung cấp "${supplier.name}"? Thao tác này không hoàn tác được.`)) {
      return;
    }
    this.run(this.supplierService.delete(supplier.id));
  }

  private run(call: Observable<unknown>): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.supplierService.notifyChanged();
        this.changed.emit();
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }
}
