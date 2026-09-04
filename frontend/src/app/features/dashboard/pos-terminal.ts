import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, debounceTime, of, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { StoreProfile } from '../../core/store/store-profile.models';
import { StoreProfileService } from '../../core/store/store-profile.service';
import { ActionErrorBanner } from './action-error-banner';
import { CouponValidation } from './coupon.models';
import { CouponService } from './coupon.service';
import { CustomerFormModal } from './customer-form-modal';
import { CustomerDTO } from './customer.models';
import { CustomerService } from './customer.service';
import { ProductDTO } from './product-admin.models';
import { ProductAdminService } from './product-admin.service';
import { CreateSaleRequest, SALE_PAYMENT_METHOD_LABELS, SaleDTO, SalePaymentMethod, SalePaymentRequest } from './sale.models';
import { SaleService } from './sale.service';
import { SplitPaymentDialog, SplitPaymentLine } from './split-payment-dialog';
import { ActionError, toActionError } from './subscription-error.util';
import { UNIT_AXIS_NAME } from './variant-builder.models';

/** 3 columns, sized to roughly fill the register's product panel with compact rows before paging - matches the density of the real KiotViet grid (its reference screenshot shows ~8 tightly-packed rows before pagination kicks in), rather than stretching a handful of tiles to fill the height. */
const GRID_PAGE_SIZE = 24;

/** "Điểm" redemption rate - matches SaleService#POINT_REDEMPTION_VALUE (1 point = 1,000đ off the invoice). */
const POINT_REDEMPTION_VALUE = 1_000;

/** Fallback "Đơn vị tính" for products that were never run through the unit/attribute builder (see UNIT_AXIS_NAME) - KiotViet always shows some unit next to the cart line's product name, not a blank. */
const DEFAULT_UNIT_LABEL = 'Cái';

/** One row of the cart, before it's turned into a SaleItemRequest at checkout. */
interface CartLine {
  /** Stable synthetic id for the @for trackBy - unlike productId, this never changes across a unit switch, so Angular updates the row's existing DOM (its <select>'s value in particular) instead of tearing it down and recreating it. */
  lineId: number;
  productId: number;
  productName: string;
  productSku: string;
  imageUrl: string | null;
  /** Variant attribute values joined for display (e.g. "Vani"), unit axis excluded - KiotViet shows this as an orange tag next to the product name in the cart line. Null for plain (non-variant) products. */
  variantLabel: string | null;
  /** This line's selling unit - the product's own "Đơn vị tính" (e.g. "Thùng") when it was generated via the unit/attribute builder, else DEFAULT_UNIT_LABEL. KiotViet always shows one next to the product name, as a dropdown once there's more than one option to switch to. */
  unitLabel: string;
  /** Sibling unit variants (same variantGroupId, matching non-unit attributes) the cashier can switch this line to - always includes at least the current product/unit itself, so the template only renders an actual <select> once there's more than one. */
  unitOptions: UnitOption[];
  unitPrice: number;
  quantity: number;
  discountAmount: number;
  /** Client-side guard so the register can't add more than what's actually in stock - server re-checks this too (with a lock), this just avoids a needless round-trip failure. */
  availableStock: number;
}

/** One entry in a cart line's unit dropdown - see CartLine#unitOptions. */
interface UnitOption {
  productId: number;
  productName: string;
  productSku: string;
  unitLabel: string;
  price: number;
  stock: number;
}

interface ProductGridState {
  query: string;
  page: number;
}

/**
 * One tile in the POS product grid. Unit-variant siblings generated together
 * (e.g. "Tryum1 - Hộp" / "Tryum1 - Lốc" - see AdminProductController's
 * `"%s - %s".formatted(baseName, attributeSuffix)`, unit axis always last)
 * collapse into a single tile keyed by variantGroupId + non-unit attributes,
 * showing the group's first product with its unit suffix stripped from the
 * name. addToCart still adds that representative product; the existing cart
 * line's unit dropdown (loadUnitSiblings) is how the cashier switches units
 * afterwards, same as picking a sibling from search. Grouping runs on
 * whatever the current GRID_PAGE_SIZE page already contains, so a page can
 * render fewer than GRID_PAGE_SIZE tiles when several unit siblings land on it together.
 */
interface GridTile {
  key: string;
  displayName: string;
  product: ProductDTO;
}

function stripUnitSuffix(product: ProductDTO): string {
  const unit = product.attributes?.[UNIT_AXIS_NAME];
  const suffix = unit ? ` - ${unit}` : null;
  return suffix && product.name.endsWith(suffix) ? product.name.slice(0, -suffix.length) : product.name;
}

function groupIntoTiles(products: ProductDTO[]): GridTile[] {
  const tiles = new Map<string, GridTile>();
  for (const product of products) {
    const otherAttrs = Object.entries(product.attributes ?? {})
      .filter(([name]) => name !== UNIT_AXIS_NAME)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([name, value]) => `${name}=${value}`)
      .join('|');
    const key = product.variantGroupId ? `${product.variantGroupId}::${otherAttrs}` : `p${product.id}`;
    if (!tiles.has(key)) {
      tiles.set(key, { key, displayName: stripUnitSuffix(product), product });
    }
  }
  return Array.from(tiles.values());
}

/** Exact amount owed + round-ups to the next 50k/100k/200k/500k VND note, deduped - same suggested-tender logic as the split-payment dialog. */
function suggestedTenderAmounts(due: number): number[] {
  if (due <= 0) {
    return [0];
  }
  const roundUpTo = (amount: number, unit: number) => Math.ceil(amount / unit) * unit;
  const candidates = [due, roundUpTo(due, 50_000), roundUpTo(due, 100_000), roundUpTo(due, 500_000)];
  return Array.from(new Set(candidates)).sort((a, b) => a - b);
}

/**
 * "Bán hàng" - the in-store POS register, reached via the "Bán hàng" button
 * in the dashboard toolbar rather than a dashboard tab (matches KiotViet's
 * own separate full-screen layout, not nested under DashboardTabs). Picks up
 * where PurchaseOrderForm's product-search/line-items patterns leave off,
 * but checkout here always finalizes immediately - see SaleService for why
 * there's no draft state, and V19's migration comment for why this is a
 * standalone module rather than a reuse of Order/Payment.
 */
@Component({
  selector: 'app-pos-terminal',
  standalone: true,
  imports: [VndCurrencyPipe, DatePipe, ActionErrorBanner, CustomerFormModal, SplitPaymentDialog],
  templateUrl: './pos-terminal.html',
})
export class PosTerminal {
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly storeProfileService = inject(StoreProfileService);
  private readonly productAdminService = inject(ProductAdminService);
  private readonly customerService = inject(CustomerService);
  private readonly saleService = inject(SaleService);
  private readonly couponService = inject(CouponService);

  readonly currentUser = this.authService.currentUser;
  readonly methodLabels = SALE_PAYMENT_METHOD_LABELS;
  readonly methods: SalePaymentMethod[] = ['CASH', 'BANK_TRANSFER', 'CARD', 'EWALLET'];

  readonly store = signal<StoreProfile | null>(null);

  constructor() {
    this.storeProfileService.getCurrentStore().subscribe({
      next: (store) => this.store.set(store),
      error: () => {},
    });
  }

  // ---- Cart ----

  readonly lines = signal<CartLine[]>([]);
  private nextLineId = 1;
  readonly note = signal('');
  readonly totalQuantity = computed(() => this.lines().reduce((sum, l) => sum + l.quantity, 0));
  readonly subtotal = computed(() => this.lines().reduce((sum, l) => sum + l.quantity * l.unitPrice - l.discountAmount, 0));

  readonly stockWarning = signal<string | null>(null);

  /** Picking a product bumps quantity by 1 if it's already in the cart, matching KiotViet's own re-scan behavior - clamped to the product's known stock so the register never lets the cart exceed what's on hand. */
  addToCart(product: ProductDTO): void {
    this.stockWarning.set(null);
    const existingIndex = this.lines().findIndex((l) => l.productId === product.id);
    if (existingIndex >= 0) {
      const line = this.lines()[existingIndex];
      if (line.quantity >= line.availableStock) {
        this.stockWarning.set(`"${product.name}" chỉ còn ${line.availableStock} trong kho.`);
        return;
      }
      this.updateLine(existingIndex, { quantity: line.quantity + 1 });
      return;
    }
    if (product.stockQuantity <= 0) {
      this.stockWarning.set(`"${product.name}" đã hết hàng.`);
      return;
    }
    const primaryImage = product.images.find((i) => i.isPrimary) ?? product.images[0];
    const explicitUnitLabel = product.attributes?.[UNIT_AXIS_NAME] ?? null;
    const unitLabel = explicitUnitLabel ?? DEFAULT_UNIT_LABEL;
    const variantLabel = Object.entries(product.attributes ?? {})
      .filter(([name]) => name !== UNIT_AXIS_NAME)
      .map(([, value]) => value)
      .join(' ') || null;
    this.lines.update((rows) => [
      ...rows,
      {
        lineId: this.nextLineId++,
        productId: product.id,
        productName: product.name,
        productSku: product.sku,
        imageUrl: primaryImage?.imageUrl ?? null,
        variantLabel,
        unitLabel,
        unitOptions: [{ productId: product.id, productName: product.name, productSku: product.sku, unitLabel, price: product.price, stock: product.stockQuantity }],
        unitPrice: product.price,
        quantity: 1,
        discountAmount: 0,
        availableStock: product.stockQuantity,
      },
    ]);
    if (explicitUnitLabel && product.variantGroupId) {
      this.loadUnitSiblings(product);
    }
  }

  /**
   * Fills in a cart line's unit dropdown once sibling unit variants come
   * back - the line is added synchronously above (with just its own unit as
   * the sole option) so the cart never blocks on this round-trip.
   */
  private loadUnitSiblings(product: ProductDTO): void {
    const otherAttributes = Object.entries(product.attributes ?? {}).filter(([name]) => name !== UNIT_AXIS_NAME);
    this.productAdminService.getUnitSiblings(product.id).subscribe({
      next: ({ products }) => {
        const options: UnitOption[] = products
          .filter(
            (sibling) =>
              !!sibling.attributes?.[UNIT_AXIS_NAME] &&
              otherAttributes.every(([name, value]) => sibling.attributes?.[name] === value),
          )
          .map((sibling) => ({
            productId: sibling.id,
            productName: sibling.name,
            productSku: sibling.sku,
            unitLabel: sibling.attributes[UNIT_AXIS_NAME],
            price: sibling.price,
            stock: sibling.stockQuantity,
          }));
        if (options.length === 0) {
          return;
        }
        const index = this.lines().findIndex((l) => l.productId === product.id);
        if (index >= 0) {
          this.updateLine(index, { unitOptions: options });
        }
      },
      error: () => {},
    });
  }

  /** The cart line's unit dropdown - switches the line to a sibling unit variant, repricing and re-clamping stock. */
  onUnitSelect(index: number, event: Event): void {
    const targetId = Number((event.target as HTMLSelectElement).value);
    const line = this.lines()[index];
    const target = line.unitOptions.find((o) => o.productId === targetId);
    if (!target || target.productId === line.productId) {
      return;
    }
    if (target.stock <= 0) {
      this.stockWarning.set(`"${line.productName}" (${target.unitLabel}) đã hết hàng.`);
      return;
    }
    this.stockWarning.set(null);
    this.updateLine(index, {
      productId: target.productId,
      productName: target.productName,
      productSku: target.productSku,
      unitLabel: target.unitLabel,
      unitPrice: target.price,
      availableStock: target.stock,
      quantity: Math.min(line.quantity, target.stock),
    });
  }

  updateLine(index: number, patch: Partial<CartLine>): void {
    this.lines.update((rows) => rows.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  }

  onQuantityInput(index: number, event: Event): void {
    const line = this.lines()[index];
    const value = Math.max(1, Number((event.target as HTMLInputElement).value) || 1);
    if (value > line.availableStock) {
      this.stockWarning.set(`"${line.productName}" chỉ còn ${line.availableStock} trong kho.`);
      this.updateLine(index, { quantity: line.availableStock });
      return;
    }
    this.updateLine(index, { quantity: value });
  }

  removeLine(index: number): void {
    this.lines.update((rows) => rows.filter((_, i) => i !== index));
  }

  onNoteInput(event: Event): void {
    this.note.set((event.target as HTMLTextAreaElement).value);
  }

  // ---- Customer search + quick add ----

  readonly customerId = signal<number | null>(null);
  readonly customerLabel = signal('');
  readonly customerSearchOpen = signal(false);
  readonly selectedCustomer = signal<CustomerDTO | null>(null);

  readonly customerSearchResults = toSignal(
    toObservable(this.customerLabel).pipe(
      debounceTime(250),
      switchMap((query) => this.customerService.list(query.trim() || undefined).pipe(catchError(() => of({ customers: [] })))),
    ),
    { initialValue: { customers: [] } },
  );

  onCustomerInput(event: Event): void {
    this.customerLabel.set((event.target as HTMLInputElement).value);
    this.customerId.set(null);
    this.selectedCustomer.set(null);
    this.customerSearchOpen.set(true);
  }

  selectCustomer(customer: CustomerDTO): void {
    this.customerId.set(customer.id);
    this.customerLabel.set(customer.name);
    this.selectedCustomer.set(customer);
    this.customerSearchOpen.set(false);
    this.usePoints.set(false);
  }

  clearCustomer(): void {
    this.customerId.set(null);
    this.customerLabel.set('');
    this.selectedCustomer.set(null);
    this.usePoints.set(false);
  }

  closeCustomerSearch(): void {
    this.customerSearchOpen.set(false);
  }

  readonly customerModalOpen = signal(false);

  openCustomerModal(): void {
    this.customerSearchOpen.set(false);
    this.customerModalOpen.set(true);
  }

  closeCustomerModal(): void {
    this.customerModalOpen.set(false);
  }

  onCustomerCreated(customer: CustomerDTO): void {
    this.customerModalOpen.set(false);
    this.selectCustomer(customer);
  }

  // ---- Product grid ----

  readonly gridState = signal<ProductGridState>({ query: '', page: 0 });

  private readonly gridResult = toSignal(
    toObservable(this.gridState).pipe(
      debounceTime(200),
      switchMap(({ query, page }) => {
        const call = query.trim().length > 0
          ? this.productAdminService.search(query.trim(), page, GRID_PAGE_SIZE)
          : this.productAdminService.list(page, GRID_PAGE_SIZE, 'name', 'ASC', true);
        return call.pipe(catchError(() => of(null)));
      }),
    ),
    { initialValue: null },
  );

  readonly gridProducts = computed(() => this.gridResult()?.products ?? []);
  readonly gridTiles = computed(() => groupIntoTiles(this.gridProducts()));
  readonly gridTotalPages = computed(() => this.gridResult()?.totalPages ?? 0);
  readonly gridPage = computed(() => this.gridState().page);

  onProductQueryInput(event: Event): void {
    this.gridState.set({ query: (event.target as HTMLInputElement).value, page: 0 });
  }

  prevGridPage(): void {
    this.gridState.update((s) => ({ ...s, page: Math.max(0, s.page - 1) }));
  }

  nextGridPage(): void {
    this.gridState.update((s) => ({ ...s, page: Math.min(this.gridTotalPages() - 1, s.page + 1) }));
  }

  /** Header refresh icon - forces gridResult's switchMap to re-run by giving gridState a new object reference (same query/page). */
  refreshGrid(): void {
    this.gridState.update((s) => ({ ...s }));
  }

  primaryImage(product: ProductDTO): string | null {
    return (product.images.find((i) => i.isPrimary) ?? product.images[0])?.imageUrl ?? null;
  }

  // ---- Checkout panel ----

  /** false = right panel shows the product grid; true = right panel shows the payment summary. Toggled by the shared "THANH TOÁN" button at the bottom - a first click opens this panel, a second one finalizes the sale (see primaryAction()). */
  readonly checkoutOpen = signal(false);
  /** Snapshot of when the payment panel was opened - shown top-right of that panel, matching KiotViet's own invoice timestamp there. */
  readonly checkoutOpenedAt = signal<Date | null>(null);

  readonly discountAmount = signal(0);
  readonly otherCollectionAmount = signal(0);

  onDiscountInput(event: Event): void {
    this.discountAmount.set(Math.max(0, Number((event.target as HTMLInputElement).value) || 0));
  }

  onOtherCollectionInput(event: Event): void {
    this.otherCollectionAmount.set(Math.max(0, Number((event.target as HTMLInputElement).value) || 0));
  }

  // ---- Mã coupon ----
  // Live preview only (GET /coupons/validate) - the actual discount applied
  // to the sale is always re-validated and re-priced server-side at
  // checkout (see SaleService), the same "never trust the client" split the
  // storefront checkout already uses for this endpoint.

  readonly couponCodeInput = signal('');
  readonly appliedCoupon = signal<CouponValidation | null>(null);
  readonly couponValidating = signal(false);
  readonly couponError = signal<string | null>(null);
  readonly couponDiscountAmount = computed(() => this.appliedCoupon()?.discountAmount ?? 0);

  onCouponCodeInput(event: Event): void {
    this.couponCodeInput.set((event.target as HTMLInputElement).value);
    this.appliedCoupon.set(null);
    this.couponError.set(null);
  }

  applyCoupon(): void {
    const code = this.couponCodeInput().trim();
    if (!code) {
      return;
    }
    this.couponValidating.set(true);
    this.couponError.set(null);
    this.couponService.validate(code, this.subtotal()).subscribe({
      next: (res) => {
        this.couponValidating.set(false);
        if (res.valid) {
          this.appliedCoupon.set(res);
        } else {
          this.couponError.set(res.message ?? 'Mã coupon không hợp lệ.');
        }
      },
      error: (err: HttpErrorResponse) => {
        this.couponValidating.set(false);
        this.couponError.set(err.error?.message ?? 'Mã coupon không hợp lệ.');
      },
    });
  }

  removeCoupon(): void {
    this.couponCodeInput.set('');
    this.appliedCoupon.set(null);
    this.couponError.set(null);
  }

  // ---- Điểm ----
  // "Dùng điểm" toggle - redeems as much of the customer's balance as the
  // remaining due allows, 1 point = 1,000đ (POINT_REDEMPTION_VALUE, matches
  // SaleService). The point count actually sent at checkout is re-clamped
  // server-side against the customer's live balance, same split as coupons.

  readonly usePoints = signal(false);
  readonly customerPoints = computed(() => this.selectedCustomer()?.loyaltyPoints ?? 0);
  readonly customerPointsValue = computed(() => this.customerPoints() * POINT_REDEMPTION_VALUE);
  private readonly amountBeforePoints = computed(() =>
    Math.max(0, this.subtotal() - this.discountAmount() - this.couponDiscountAmount()),
  );
  readonly pointsRedeemedAmount = computed(() =>
    this.usePoints() ? Math.min(this.customerPointsValue(), this.amountBeforePoints()) : 0,
  );
  readonly pointsToRedeem = computed(() => Math.floor(this.pointsRedeemedAmount() / POINT_REDEMPTION_VALUE));

  togglePoints(): void {
    this.usePoints.update((v) => !v);
  }

  readonly totalAmount = computed(() =>
    Math.max(
      0,
      this.subtotal() - this.discountAmount() - this.couponDiscountAmount() - this.pointsRedeemedAmount() + this.otherCollectionAmount(),
    ),
  );

  /** Single-tender path: which of the 4 radio buttons is active, and how much is tendered (may exceed totalAmount for cash - see changeAmount). Cleared whenever a split payment is confirmed. */
  readonly selectedMethod = signal<SalePaymentMethod>('CASH');
  readonly singleTenderAmount = signal(0);
  readonly splitLines = signal<SplitPaymentLine[] | null>(null);
  readonly splitDialogOpen = signal(false);

  /**
   * Keeps the single-tender "Khách thanh toán" amount tracking the total by
   * default (so editing Giảm giá/Thu khác doesn't leave a stale amount
   * behind), as long as the cashier hasn't picked a bigger quick-cash amount
   * and no split payment is in progress.
   */
  private readonly trackSingleTenderAmount = effect(() => {
    const total = this.totalAmount();
    if (this.splitLines() === null && this.singleTenderAmount() < total) {
      this.singleTenderAmount.set(total);
    }
  });

  readonly quickTenderAmounts = computed(() => suggestedTenderAmounts(this.totalAmount()));

  selectMethod(method: SalePaymentMethod): void {
    this.selectedMethod.set(method);
    this.splitLines.set(null);
    this.singleTenderAmount.set(this.totalAmount());
  }

  setQuickTenderAmount(amount: number): void {
    this.splitLines.set(null);
    this.singleTenderAmount.set(amount);
  }

  openSplitDialog(): void {
    this.splitDialogOpen.set(true);
  }

  closeSplitDialog(): void {
    this.splitDialogOpen.set(false);
  }

  onSplitConfirmed(lines: SplitPaymentLine[]): void {
    this.splitLines.set(lines);
    this.splitDialogOpen.set(false);
  }

  readonly paymentRequestLines = computed<SalePaymentRequest[]>(() => {
    const split = this.splitLines();
    if (split && split.length > 0) {
      return split.map((l) => ({ method: l.method, amount: l.amount }));
    }
    return [{ method: this.selectedMethod(), amount: this.singleTenderAmount() }];
  });

  readonly amountTendered = computed(() => this.paymentRequestLines().reduce((sum, l) => sum + l.amount, 0));
  readonly changeAmount = computed(() => Math.max(0, this.amountTendered() - this.totalAmount()));

  // ---- Save / finalize ----

  readonly submitting = signal(false);
  readonly actionError = signal<ActionError | null>(null);
  readonly completedSale = signal<SaleDTO | null>(null);

  /** The shared "THANH TOÁN" button: opens the payment panel the first time, finalizes the sale once it's open. */
  primaryAction(): void {
    if (!this.checkoutOpen()) {
      if (this.lines().length === 0) {
        this.actionError.set({ message: 'Chưa chọn hàng hóa nào.', isUpgradeRequired: false });
        return;
      }
      this.actionError.set(null);
      this.checkoutOpen.set(true);
      this.checkoutOpenedAt.set(new Date());
      return;
    }
    this.finalizeSale();
  }

  backToCart(): void {
    this.checkoutOpen.set(false);
    this.checkoutOpenedAt.set(null);
    this.actionError.set(null);
  }

  private finalizeSale(): void {
    this.submitting.set(true);
    this.actionError.set(null);
    const request: CreateSaleRequest = {
      customerId: this.customerId(),
      discountAmount: this.discountAmount(),
      otherCollectionAmount: this.otherCollectionAmount(),
      couponCode: this.appliedCoupon()?.code ?? null,
      pointsToRedeem: this.pointsToRedeem(),
      note: this.note(),
      items: this.lines().map((l) => ({
        productId: l.productId,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        discountAmount: l.discountAmount,
      })),
      payments: this.paymentRequestLines(),
    };
    this.saleService.checkout(request).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.completedSale.set(res.sale);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  printReceipt(): void {
    window.print();
  }

  /** Resets the whole register for the next customer. */
  startNewSale(): void {
    this.lines.set([]);
    this.note.set('');
    this.clearCustomer();
    this.checkoutOpen.set(false);
    this.checkoutOpenedAt.set(null);
    this.discountAmount.set(0);
    this.otherCollectionAmount.set(0);
    this.removeCoupon();
    this.selectedMethod.set('CASH');
    this.singleTenderAmount.set(0);
    this.splitLines.set(null);
    this.completedSale.set(null);
    this.actionError.set(null);
  }

  exit(): void {
    this.router.navigate(['/dashboard']);
  }
}
