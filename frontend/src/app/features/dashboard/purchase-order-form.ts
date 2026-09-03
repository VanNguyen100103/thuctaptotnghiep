import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, of, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { ActionErrorBanner } from './action-error-banner';
import { ProductDTO } from './product-admin.models';
import { ProductAdminService } from './product-admin.service';
import { PurchaseOrderDTO, PurchaseOrderItemRequest, PurchaseOrderStatus, SavePurchaseOrderRequest } from './purchase-order.models';
import { PurchaseOrderService } from './purchase-order.service';
import { SupplierFormModal } from './supplier-form-modal';
import { SupplierDTO } from './supplier.models';
import { SupplierService } from './supplier.service';
import { ActionError, toActionError } from './subscription-error.util';

/** One editable row in the line-items table, before it's turned into a PurchaseOrderItemRequest on save. */
interface DraftLine {
  productId: number;
  productName: string;
  productSku: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
}

/** Passed via Router navigation state (not the URL) from duplicateOrder() to a fresh "/new" form instance. */
interface DuplicatePurchaseOrderState {
  duplicateFromCode: string;
  supplierId: number | null;
  supplierLabel: string;
  note: string;
  lines: DraftLine[];
}

@Component({
  selector: 'app-purchase-order-form',
  standalone: true,
  imports: [RouterLink, VndCurrencyPipe, DatePipe, ActionErrorBanner, SupplierFormModal],
  templateUrl: './purchase-order-form.html',
})
export class PurchaseOrderForm {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly productAdminService = inject(ProductAdminService);
  private readonly supplierService = inject(SupplierService);
  private readonly purchaseOrderService = inject(PurchaseOrderService);

  readonly currentUser = this.authService.currentUser;

  /**
   * Two ways to host this component: routed (its own page, at "/new" or
   * "/:id") or embedded inline inside PurchaseOrderList's own row, which is
   * how KiotViet's real list actually opens an existing receipt - clicking a
   * row expands its detail right there, it doesn't navigate to a new page
   * (only "+ Nhập hàng" does that, matching KiotViet's own "/new" flow).
   */
  readonly embedded = input(false);
  readonly embeddedId = input<number | null>(null);
  readonly closed = output<void>();

  private readonly paramMap = toSignal(this.route.paramMap, { requireSync: true });
  readonly purchaseOrderId = computed(() => {
    if (this.embedded()) {
      return this.embeddedId();
    }
    const raw = this.paramMap()!.get('id');
    return raw ? Number(raw) : null;
  });
  readonly isEditMode = computed(() => this.purchaseOrderId() !== null);

  readonly loaded = signal<PurchaseOrderDTO | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly createdAt = signal<string>(new Date().toISOString());

  readonly status = computed<PurchaseOrderStatus>(() => this.loaded()?.status ?? 'DRAFT');
  readonly isReadOnly = computed(() => this.status() !== 'DRAFT');
  readonly code = computed(() => this.loaded()?.code ?? null);
  readonly createdByLabel = computed(() => this.loaded()?.createdByUsername ?? this.currentUser()?.username ?? 'Admin');
  /** "Người nhập" - only meaningful once completed. */
  readonly completedByLabel = computed(() => this.loaded()?.completedByUsername ?? null);
  readonly totalQuantity = computed(() => this.lines().reduce((sum, l) => sum + l.quantity, 0));

  /** Reactively (re)loads whenever purchaseOrderId changes - needed for the embedded case, where switching which row is expanded changes the input without recreating the component. */
  private readonly fetchedPurchaseOrder = toSignal(
    toObservable(this.purchaseOrderId).pipe(
      switchMap((id) =>
        id === null
          ? of(null)
          : this.purchaseOrderService.getById(id).pipe(catchError((err: HttpErrorResponse) => {
              this.loadError.set(err.message);
              return of(null);
            })),
      ),
    ),
    { initialValue: null },
  );

  constructor() {
    effect(() => {
      const po = this.fetchedPurchaseOrder();
      if (po) {
        this.applyLoaded(po);
      }
    });
    if (this.purchaseOrderId() === null) {
      this.applyDuplicateStateIfAny();
    }
  }

  /** Pre-fills a fresh "/new" form from "Sao chép" on a completed order - see duplicateOrder(). Payment/adjustment amounts are deliberately reset (each delivery is its own transaction), only supplier + line items + note carry over. */
  private applyDuplicateStateIfAny(): void {
    const state = window.history.state as DuplicatePurchaseOrderState | undefined;
    if (!state?.duplicateFromCode) {
      return;
    }
    this.supplierId.set(state.supplierId);
    this.supplierLabel.set(state.supplierLabel);
    this.note.set(state.note);
    this.lines.set(state.lines);
  }

  private applyLoaded(po: PurchaseOrderDTO): void {
    this.loaded.set(po);
    this.createdAt.set(po.createdAt);
    this.supplierId.set(po.supplierId);
    this.supplierLabel.set(po.supplierName ? `${po.supplierCode} - ${po.supplierName}` : '');
    this.discountAmount.set(po.discountAmount);
    this.supplierChargeAmount.set(po.supplierChargeAmount);
    this.amountPaid.set(po.amountPaid);
    this.otherCosts.set(po.otherCosts);
    this.note.set(po.note ?? '');
    this.lines.set(
      (po.items ?? []).map((item) => ({
        productId: item.productId,
        productName: item.productName,
        productSku: item.productSku,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        discountAmount: item.discountAmount,
      })),
    );
  }

  // ---- Line items ----

  readonly lines = signal<DraftLine[]>([]);

  readonly totalGoodsValue = computed(() =>
    this.lines().reduce((sum, l) => sum + l.quantity * l.unitPrice - l.discountAmount, 0),
  );

  lineTotal(line: DraftLine): number {
    return line.quantity * line.unitPrice - line.discountAmount;
  }

  updateLine(index: number, patch: Partial<DraftLine>): void {
    this.lines.update((rows) => rows.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  }

  onQuantityInput(index: number, event: Event): void {
    const value = Number((event.target as HTMLInputElement).value) || 1;
    this.updateLine(index, { quantity: Math.max(1, value) });
  }

  onUnitPriceInput(index: number, event: Event): void {
    const value = Number((event.target as HTMLInputElement).value) || 0;
    this.updateLine(index, { unitPrice: Math.max(0, value) });
  }

  onLineDiscountInput(index: number, event: Event): void {
    const value = Number((event.target as HTMLInputElement).value) || 0;
    this.updateLine(index, { discountAmount: Math.max(0, value) });
  }

  removeLine(index: number): void {
    this.lines.update((rows) => rows.filter((_, i) => i !== index));
  }

  // ---- Product search ----

  readonly productSearchQuery = signal('');
  readonly productSearchOpen = signal(false);

  readonly productSearchResults = toSignal(
    toObservable(this.productSearchQuery).pipe(
      debounceTime(250),
      switchMap((query) =>
        query.trim().length > 0
          ? this.productAdminService.search(query.trim(), 0, 8).pipe(catchError(() => of(null)))
          : of(null),
      ),
    ),
    { initialValue: null },
  );

  onProductSearchInput(event: Event): void {
    this.productSearchQuery.set((event.target as HTMLInputElement).value);
    this.productSearchOpen.set(true);
  }

  closeProductSearch(): void {
    this.productSearchOpen.set(false);
  }

  /** Picking a result adds a new line, or bumps quantity by 1 if that product is already in the table - matches KiotViet's own re-scan behavior. */
  selectProduct(product: ProductDTO): void {
    const existingIndex = this.lines().findIndex((l) => l.productId === product.id);
    if (existingIndex >= 0) {
      this.updateLine(existingIndex, { quantity: this.lines()[existingIndex].quantity + 1 });
    } else {
      this.lines.update((rows) => [
        ...rows,
        {
          productId: product.id,
          productName: product.name,
          productSku: product.sku,
          quantity: 1,
          unitPrice: product.costPrice ?? product.price,
          discountAmount: 0,
        },
      ]);
    }
    this.productSearchQuery.set('');
    this.productSearchOpen.set(false);
  }

  // ---- Supplier search + quick add ----

  readonly supplierId = signal<number | null>(null);
  readonly supplierLabel = signal('');
  readonly supplierSearchOpen = signal(false);

  readonly supplierSearchResults = toSignal(
    toObservable(this.supplierLabel).pipe(
      debounceTime(250),
      switchMap((query) => this.supplierService.list(query.trim() || undefined).pipe(catchError(() => of({ suppliers: [] })))),
    ),
    { initialValue: { suppliers: [] } },
  );

  onSupplierInput(event: Event): void {
    this.supplierLabel.set((event.target as HTMLInputElement).value);
    this.supplierId.set(null);
    this.supplierSearchOpen.set(true);
  }

  selectSupplier(supplier: SupplierDTO): void {
    this.supplierId.set(supplier.id);
    this.supplierLabel.set(`${supplier.code} - ${supplier.name}`);
    this.supplierSearchOpen.set(false);
  }

  closeSupplierSearch(): void {
    this.supplierSearchOpen.set(false);
  }

  /** "Tạo nhà cung cấp" modal, opened from the "+" button - same shared modal as SupplierList, pre-filled with whatever's typed in the search box. */
  readonly supplierModalOpen = signal(false);

  openSupplierModal(): void {
    this.supplierSearchOpen.set(false);
    this.supplierModalOpen.set(true);
  }

  closeSupplierModal(): void {
    this.supplierModalOpen.set(false);
  }

  onSupplierCreated(supplier: SupplierDTO): void {
    this.supplierModalOpen.set(false);
    this.selectSupplier(supplier);
  }

  // ---- Header fields ----

  readonly discountAmount = signal(0);
  /** "Chi phí nhập trả NCC" - extra charge the supplier itself bills, adds to payableAmount. */
  readonly supplierChargeAmount = signal(0);
  /** "Tiền trả nhà cung cấp (F8)" - cash/transfer paid right now, at receipt time. */
  readonly amountPaid = signal(0);
  readonly otherCosts = signal(0);
  readonly note = signal('');

  onDiscountInput(event: Event): void {
    this.discountAmount.set(Math.max(0, Number((event.target as HTMLInputElement).value) || 0));
  }

  onSupplierChargeInput(event: Event): void {
    this.supplierChargeAmount.set(Math.max(0, Number((event.target as HTMLInputElement).value) || 0));
  }

  onAmountPaidInput(event: Event): void {
    this.amountPaid.set(Math.max(0, Number((event.target as HTMLInputElement).value) || 0));
  }

  onOtherCostsInput(event: Event): void {
    this.otherCosts.set(Math.max(0, Number((event.target as HTMLInputElement).value) || 0));
  }

  onNoteInput(event: Event): void {
    this.note.set((event.target as HTMLTextAreaElement).value);
  }

  /** "Cần trả nhà cung cấp" - gross obligation for this delivery, before today's payment. otherCosts is deliberately excluded, mirrors PurchaseOrder#payableAmount on the backend (it's paid to a 3rd party, not the supplier). */
  readonly payableAmount = computed(() => this.totalGoodsValue() - this.discountAmount() + this.supplierChargeAmount());

  /** "Tính vào công nợ" = amountPaid - payableAmount - the remainder (usually negative) booked to the supplier's running debt, same accounting convention as the backend's PurchaseOrderResponse#debtAmount. */
  readonly debtAmount = computed(() => this.amountPaid() - this.payableAmount());

  // ---- Save / complete / cancel ----

  readonly submitting = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  private buildRequest(): SavePurchaseOrderRequest {
    const items: PurchaseOrderItemRequest[] = this.lines().map((l) => ({
      productId: l.productId,
      quantity: l.quantity,
      unitPrice: l.unitPrice,
      discountAmount: l.discountAmount,
    }));
    return {
      supplierId: this.supplierId(),
      discountAmount: this.discountAmount(),
      supplierChargeAmount: this.supplierChargeAmount(),
      amountPaid: this.amountPaid(),
      otherCosts: this.otherCosts(),
      note: this.note(),
      items,
    };
  }

  private persist(onSuccess: (saved: PurchaseOrderDTO) => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    const request = this.buildRequest();
    const id = this.purchaseOrderId();
    const call = id !== null ? this.purchaseOrderService.update(id, request) : this.purchaseOrderService.create(request);
    call.subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.purchaseOrderService.notifyChanged();
        onSuccess(res.purchaseOrder);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  /** "Lưu tạm" - saves whatever is on the form as a draft, even with zero lines. */
  saveDraft(): void {
    this.persist((saved) => {
      this.applyLoaded(saved);
      if (!this.isEditMode()) {
        this.router.navigate(['/dashboard/purchase-orders', saved.id]);
      }
    });
  }

  /** "Hoàn thành" - saves the current form state first (so no unsaved edit is lost), then locks it and applies stock. */
  finishOrder(): void {
    if (this.lines().length === 0) {
      this.actionError.set({ message: 'Chưa có hàng hóa nào trong phiếu nhập.', isUpgradeRequired: false });
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    const request = this.buildRequest();
    const id = this.purchaseOrderId();
    const saveCall = id !== null ? this.purchaseOrderService.update(id, request) : this.purchaseOrderService.create(request);
    saveCall.subscribe({
      next: (saveRes) => {
        this.purchaseOrderService.complete(saveRes.purchaseOrder.id).subscribe({
          next: (completeRes) => {
            this.submitting.set(false);
            this.purchaseOrderService.notifyChanged();
            this.applyLoaded(completeRes.purchaseOrder);
            if (!this.isEditMode()) {
              this.router.navigate(['/dashboard/purchase-orders', completeRes.purchaseOrder.id]);
            }
          },
          error: (err: HttpErrorResponse) => {
            this.submitting.set(false);
            this.actionError.set(toActionError(err));
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  /** "Hủy phiếu" - only available on an already-saved draft. */
  cancelOrder(): void {
    const id = this.purchaseOrderId();
    if (id === null) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.purchaseOrderService.cancel(id).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.purchaseOrderService.notifyChanged();
        this.applyLoaded(res.purchaseOrder);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  /** "Sao chép" - opens a fresh draft pre-filled from this (completed/cancelled) document's supplier + line items, for a new delivery of the same goods. Payment/adjustment amounts are reset, not copied - see applyDuplicateStateIfAny(). */
  duplicateOrder(): void {
    const po = this.loaded();
    if (!po) {
      return;
    }
    const state: DuplicatePurchaseOrderState = {
      duplicateFromCode: po.code,
      supplierId: po.supplierId,
      supplierLabel: po.supplierName ? `${po.supplierCode} - ${po.supplierName}` : '',
      note: po.note ?? '',
      lines: (po.items ?? []).map((item) => ({
        productId: item.productId,
        productName: item.productName,
        productSku: item.productSku,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        discountAmount: item.discountAmount,
      })),
    };
    this.router.navigate(['/dashboard/purchase-orders/new'], { state });
  }

  /** "←" back / collapse - navigates away when this is its own page, or just collapses the row when embedded inline inside PurchaseOrderList. */
  close(): void {
    if (this.embedded()) {
      this.closed.emit();
    } else {
      this.router.navigate(['/dashboard/purchase-orders']);
    }
  }
}
