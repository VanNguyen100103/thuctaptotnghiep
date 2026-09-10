import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, debounceTime, map, of, startWith, switchMap, tap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { VndWordsPipe } from '../../core/currency/vnd-words.pipe';
import { StoreProfile } from '../../core/store/store-profile.models';
import { StoreProfileService } from '../../core/store/store-profile.service';
import { ActionErrorBanner } from './action-error-banner';
import { CouponValidation } from './coupon.models';
import { CouponService } from './coupon.service';
import { CustomerFormModal } from './customer-form-modal';
import { CustomerDTO } from './customer.models';
import { CustomerService } from './customer.service';
import {
  CreateShipmentRequest,
  INSPECTION_POLICY_LABELS,
  InspectionPolicy,
  LocationOption,
  ShipmentDTO,
  ShippingRate,
} from './shipment.models';
import { ShipmentService } from './shipment.service';
import { ProductDTO } from './product-admin.models';
import { ProductAdminService } from './product-admin.service';
import {
  CreateSaleRequest,
  SALE_PAYMENT_METHOD_LABELS,
  SaleDTO,
  SaleDeliveryRequest,
  SalePaymentMethod,
  SalePaymentRequest,
} from './sale.models';
import { SaleService } from './sale.service';
import { SepayQrService } from './sepay-qr.service';
import { SplitPaymentDialog, SplitPaymentLine } from './split-payment-dialog';
import { ActionError, toActionError } from './subscription-error.util';
import { UNIT_AXIS_NAME } from './variant-builder.models';

/**
 * Tiles per grid page: 4 columns x 4 rows on a wide screen, which is what
 * the panel holds without the grid having to scroll. 24 filled the space
 * but pushed the last row under the fold, leaving the cashier scrolling
 * inside a page as well as paging through them.
 *
 * It was 9 before that, on the reasoning that a bigger page only opens a
 * blank gap under a sparse catalog - true for a handful of products, but a
 * real catalog left two thirds of the panel empty. Now that a tile is one
 * product rather than one variant group (see GridTile), every page but the
 * last renders exactly this many.
 */
const GRID_PAGE_SIZE = 16;

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
 * One tile in the POS product grid - one per product, unit-variant siblings
 * included (e.g. "Tryum1 - Hộp" and "Tryum1 - Lốc" each get their own; see
 * AdminProductController's `"%s - %s".formatted(baseName, attributeSuffix)`,
 * unit axis always last).
 *
 * These used to collapse into one tile per variant group, which meant a page
 * of GRID_PAGE_SIZE products rendered anywhere from a handful of tiles to a
 * full page depending on how many siblings happened to land on it together -
 * a grid whose row count moved as the cashier paged through it. Keeping them
 * apart also saves a step at the register: tapping the unit the customer is
 * actually buying beats adding a representative product and then changing
 * the cart line's unit dropdown.
 *
 * The unit is lifted out of the name into `unit` so the tile can show it as
 * its own chip - otherwise two tiles sharing a base name are told apart only
 * by price, and a truncated name hides the difference entirely.
 */
interface GridTile {
  key: string;
  displayName: string;
  unit: string | null;
  product: ProductDTO;
}

function stripUnitSuffix(product: ProductDTO): string {
  const unit = product.attributes?.[UNIT_AXIS_NAME];
  const suffix = unit ? ` - ${unit}` : null;
  return suffix && product.name.endsWith(suffix) ? product.name.slice(0, -suffix.length) : product.name;
}

function toTile(product: ProductDTO): GridTile {
  return {
    key: `p${product.id}`,
    displayName: stripUnitSuffix(product),
    unit: product.attributes?.[UNIT_AXIS_NAME] ?? null,
    product,
  };
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
  imports: [VndCurrencyPipe, VndWordsPipe, DatePipe, DecimalPipe, ActionErrorBanner, CustomerFormModal, SplitPaymentDialog],
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
  private readonly sepayQrService = inject(SepayQrService);
  private readonly shipmentService = inject(ShipmentService);

  readonly currentUser = this.authService.currentUser;
  readonly methodLabels = SALE_PAYMENT_METHOD_LABELS;

  /**
   * "Ngày 08 tháng 09 năm 2026" - the long form a Vietnamese receipt prints.
   * Kept here rather than inline in the template because the format string's
   * own quoted literals fight with the binding's quotes.
   */
  readonly receiptDateFormat = "'Ngày' dd 'tháng' MM 'năm' yyyy";
  readonly methods: SalePaymentMethod[] = ['CASH', 'BANK_TRANSFER', 'CARD', 'EWALLET'];

  /**
   * When this invoice was opened, printed at the top of the delivery panel
   * the way KiotViet stamps its own. Deliberately not a ticking clock - it
   * dates the invoice, so it is fixed at the moment the register was opened.
   */
  readonly saleStartedAt = new Date();

  readonly store = signal<StoreProfile | null>(null);

  constructor() {
    this.storeProfileService.getCurrentStore().subscribe({
      next: (store) => this.store.set(store),
      error: () => {},
    });

    // The receipt has no on-screen dialog to dismiss any more, so closing
    // the print dialog is what ends the sale - printed or cancelled, the
    // register clears for the next customer. Guarded on completedSale so a
    // stray Ctrl+P while a cart is still being rung up cannot wipe it.
    const onAfterPrint = () => {
      if (this.completedSale()) {
        this.startNewSale();
      }
    };
    window.addEventListener('afterprint', onAfterPrint);
    inject(DestroyRef).onDestroy(() => window.removeEventListener('afterprint', onAfterPrint));
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

  // ---- Sale mode: footer tabs (KiotViet's "Bán nhanh" / "Bán thường" / "Bán giao hàng") ----

  readonly saleMode = signal<'normal' | 'delivery'>('normal');

  setSaleMode(mode: 'normal' | 'delivery'): void {
    this.saleMode.set(mode);
    if (mode === 'delivery' && this.deliveryProvinces().length === 0) {
      this.loadDeliveryProvinces();
    }
  }

  // ---- "Bán giao hàng": recipient, address, package, carrier ----
  // Tỉnh/Quận/Phường cascade through Goship's address data, which is what
  // its rate and booking calls take. Goship names the top level "city"; the
  // signals below keep the Vietnamese sense (Tỉnh/Thành) they always had, so
  // deliveryProvinceId holds what its API calls a city id.
  //
  // Tổ dân phố/Khu phố are extra, optional detail lines this form also
  // collects (Vietnam's 2025 reform folded these in as informal sub-units
  // once Quận/Huyện was dropped nationwide) - not part of anyone's address
  // model, just appended to the printed address for extra precision.

  readonly deliveryName = signal('');
  readonly deliveryPhone = signal('');
  readonly deliveryAddress = signal('');
  readonly deliveryHamlet = signal('');
  readonly deliveryNeighborhood = signal('');
  readonly deliveryNote = signal('');

  readonly deliveryProvinceId = signal('');
  readonly deliveryDistrictId = signal('');
  readonly deliveryWardCode = signal('');
  readonly deliveryProvinces = signal<LocationOption[]>([]);
  readonly deliveryDistricts = signal<LocationOption[]>([]);
  readonly deliveryWards = signal<LocationOption[]>([]);
  readonly loadingDeliveryProvinces = signal(false);
  readonly loadingDeliveryDistricts = signal(false);
  readonly loadingDeliveryWards = signal(false);

  readonly packageWeightGrams = signal(500);
  readonly packageLengthCm = signal(10);
  readonly packageWidthCm = signal(10);
  readonly packageHeightCm = signal(10);

  /** Prefills the recipient from the selected customer the first time one is picked in this sale - still freely editable afterwards, same as KiotViet. */
  private readonly prefillDeliveryRecipient = effect(() => {
    const customer = this.selectedCustomer();
    if (customer && !this.deliveryName().trim()) {
      this.deliveryName.set(customer.name);
      this.deliveryPhone.set(customer.phone ?? '');
      this.deliveryAddress.set(customer.address ?? '');
    }
  });

  private loadDeliveryProvinces(): void {
    this.loadingDeliveryProvinces.set(true);
    this.shipmentService.cities().subscribe({
      next: (res) => {
        this.loadingDeliveryProvinces.set(false);
        this.deliveryProvinces.set(res.cities);
      },
      error: () => this.loadingDeliveryProvinces.set(false),
    });
  }

  onDeliveryNameInput(event: Event): void {
    this.deliveryName.set((event.target as HTMLInputElement).value);
  }

  onDeliveryPhoneInput(event: Event): void {
    this.deliveryPhone.set((event.target as HTMLInputElement).value);
  }

  onDeliveryAddressInput(event: Event): void {
    this.deliveryAddress.set((event.target as HTMLInputElement).value);
  }

  onDeliveryHamletInput(event: Event): void {
    this.deliveryHamlet.set((event.target as HTMLInputElement).value);
  }

  onDeliveryNeighborhoodInput(event: Event): void {
    this.deliveryNeighborhood.set((event.target as HTMLInputElement).value);
  }

  onDeliveryNoteInput(event: Event): void {
    this.deliveryNote.set((event.target as HTMLTextAreaElement).value);
  }

  onDeliveryProvinceChange(event: Event): void {
    const provinceId = (event.target as HTMLSelectElement).value;
    this.deliveryProvinceId.set(provinceId);
    this.deliveryDistrictId.set('');
    this.deliveryWardCode.set('');
    this.deliveryDistricts.set([]);
    this.deliveryWards.set([]);
    if (!provinceId) {
      return;
    }
    this.loadingDeliveryDistricts.set(true);
    this.shipmentService.districts(provinceId).subscribe({
      next: (res) => {
        this.loadingDeliveryDistricts.set(false);
        this.deliveryDistricts.set(res.districts);
      },
      error: () => this.loadingDeliveryDistricts.set(false),
    });
  }

  onDeliveryDistrictChange(event: Event): void {
    const districtId = (event.target as HTMLSelectElement).value;
    this.deliveryDistrictId.set(districtId);
    this.deliveryWardCode.set('');
    this.deliveryWards.set([]);
    if (!districtId) {
      return;
    }
    this.loadingDeliveryWards.set(true);
    this.shipmentService.wards(districtId).subscribe({
      next: (res) => {
        this.loadingDeliveryWards.set(false);
        this.deliveryWards.set(res.wards);
      },
      error: () => this.loadingDeliveryWards.set(false),
    });
  }

  onDeliveryWardChange(event: Event): void {
    this.deliveryWardCode.set((event.target as HTMLSelectElement).value);
  }

  onPackageWeightInput(event: Event): void {
    this.packageWeightGrams.set(Math.max(1, Number((event.target as HTMLInputElement).value) || 1));
  }

  onPackageLengthInput(event: Event): void {
    this.packageLengthCm.set(Math.max(1, Number((event.target as HTMLInputElement).value) || 1));
  }

  onPackageWidthInput(event: Event): void {
    this.packageWidthCm.set(Math.max(1, Number((event.target as HTMLInputElement).value) || 1));
  }

  onPackageHeightInput(event: Event): void {
    this.packageHeightCm.set(Math.max(1, Number((event.target as HTMLInputElement).value) || 1));
  }

  readonly deliveryAddressComplete = computed(
    () => !!(this.deliveryProvinceId() && this.deliveryDistrictId() && this.deliveryWardCode()),
  );

  /** Full delivery address assembled for the receipt, small-to-large: số nhà/đường, tổ dân phố, khu phố, phường/xã, quận/huyện, tỉnh/thành phố. */
  readonly fullDeliveryAddress = computed(() => {
    const ward = this.deliveryWards().find((w) => w.id === this.deliveryWardCode())?.name ?? '';
    const district = this.deliveryDistricts().find((d) => d.id === this.deliveryDistrictId())?.name ?? '';
    const province = this.deliveryProvinces().find((p) => p.id === this.deliveryProvinceId())?.name ?? '';
    return [this.deliveryAddress(), this.deliveryHamlet(), this.deliveryNeighborhood(), ward, district, province]
      .map((part) => part.trim())
      .filter(Boolean)
      .join(', ');
  });

  private deliveryFormValid(): boolean {
    return (
      this.deliveryName().trim().length > 0 &&
      this.deliveryPhone().trim().length > 0 &&
      this.deliveryAddress().trim().length > 0 &&
      this.deliveryAddressComplete()
    );
  }

  /**
   * "Cổng KiotViet" vs "Tự giao hàng" tabs, matching KiotViet's own screen.
   * The carrier list under the gateway tab used to be one live GHN row above
   * six disabled placeholders; it is now every carrier Goship serves this
   * route with, priced, and any of them can be picked.
   */
  readonly deliveryGatewayTab = signal<'gateway' | 'self'>('gateway');
  readonly gatewayServiceTab = signal<'standard' | 'priority' | 'fast'>('standard');
  readonly codEnabled = signal(true);

  setDeliveryGatewayTab(tab: 'gateway' | 'self'): void {
    this.deliveryGatewayTab.set(tab);
  }

  setGatewayServiceTab(tab: 'standard' | 'priority' | 'fast'): void {
    if (tab === 'standard') {
      this.gatewayServiceTab.set(tab);
    }
  }

  toggleCod(): void {
    this.codEnabled.update((v) => !v);
  }

  /**
   * Every carrier's price for this parcel, recomputed whenever the address
   * or the package changes - same debounce pattern as bankTransferSession
   * below.
   *
   * Only the city and district are priced on, which is why the ward is
   * absent here: Goship quotes at district level and only wants the ward
   * when the shipment is actually booked.
   */
  private readonly rateQuery = computed(() => ({
    cityId: this.deliveryProvinceId(),
    districtId: this.deliveryDistrictId(),
    weight: this.packageWeightGrams(),
    length: this.packageLengthCm(),
    width: this.packageWidthCm(),
    height: this.packageHeightCm(),
  }));

  readonly ratesState = toSignal(
    toObservable(this.rateQuery).pipe(
      debounceTime(400),
      switchMap((q) => {
        if (!q.cityId || !q.districtId) {
          return of<{ rates: ShippingRate[]; error: string | null }>({ rates: [], error: null });
        }
        return this.shipmentService
          .rates({
            toCityId: q.cityId,
            toDistrictId: q.districtId,
            weightGrams: q.weight,
            lengthCm: q.length,
            widthCm: q.width,
            heightCm: q.height,
          })
          .pipe(
            map((res) => ({ rates: res.rates, error: null })),
            catchError((err: HttpErrorResponse) =>
              of({ rates: [] as ShippingRate[], error: err.error?.error ?? 'Không tính được cước vận chuyển.' }),
            ),
          );
      }),
    ),
    { initialValue: { rates: [] as ShippingRate[], error: null } },
  );

  readonly rates = computed(() => this.ratesState().rates);
  readonly selectedRateId = signal<string | null>(null);
  readonly selectedRate = computed(() => this.rates().find((r) => r.id === this.selectedRateId()) ?? null);

  /**
   * A rate id belongs to the quote it came from, so a fresh quote makes the
   * previous selection meaningless - booking against it would either be
   * refused or ship at a price nobody agreed to. Falls back to the cheapest,
   * which the backend sorts to the front.
   */
  private readonly keepRateSelectionValid = effect(() => {
    const rates = this.rates();
    untracked(() => {
      const current = this.selectedRateId();
      if (rates.length === 0) {
        if (current !== null) {
          this.selectedRateId.set(null);
        }
        return;
      }
      if (!rates.some((rate) => rate.id === current)) {
        this.selectedRateId.set(rates[0].id);
      }
    });
  });

  selectRate(rateId: string): void {
    this.selectedRateId.set(rateId);
  }

  /**
   * The two carrier options Goship actually has a field for.
   *
   * KiotViet's panel offers seven; the other five (Gửi tại bưu cục, Hàng giá
   * trị cao, Đối soát nhanh, Thu tiền xem hàng, Không cho xem hàng) have no
   * counterpart in Goship's shipment body, and a checkbox that changes
   * nothing is worse than no checkbox - the register just spent a change
   * removing six carrier rows exactly like that.
   */
  readonly declaredValueEnabled = signal(true);
  readonly senderPaysShipping = signal(true);

  /**
   * The one KiotViet option Goship cannot enforce but can still carry: it
   * goes out as a note on the parcel and is printed on the delivery slip,
   * which is where the courier at the door reads it.
   *
   * Defaults to no inspection, the safer of the three for a shop - a parcel
   * opened before payment can be refused after handling.
   */
  readonly inspectionPolicy = signal<InspectionPolicy>('NO_INSPECTION');
  readonly inspectionPolicyLabels = INSPECTION_POLICY_LABELS;
  readonly inspectionPolicies: InspectionPolicy[] = ['NO_INSPECTION', 'VIEW_ONLY', 'TRIAL_ALLOWED'];

  toggleDeclaredValue(): void {
    this.declaredValueEnabled.update((v) => !v);
  }

  toggleSenderPaysShipping(): void {
    this.senderPaysShipping.update((v) => !v);
  }

  onInspectionPolicyChange(event: Event): void {
    this.inspectionPolicy.set((event.target as HTMLSelectElement).value as InspectionPolicy);
  }

  /** "Tổng số sản phẩm" on the delivery slip - what the courier counts against. */
  readonly completedSaleQuantity = computed(() =>
    (this.completedSale()?.items ?? []).reduce((sum, item) => sum + item.quantity, 0),
  );

  // ---- shipment booking on checkout ----

  readonly creatingShipment = signal(false);
  readonly createdShipment = signal<ShipmentDTO | null>(null);
  readonly shipmentError = signal<string | null>(null);

  private createDeliveryShipment(sale: SaleDTO, orderId?: number): void {
    const province = this.deliveryProvinces().find((p) => p.id === this.deliveryProvinceId());
    const district = this.deliveryDistricts().find((d) => d.id === this.deliveryDistrictId());
    const ward = this.deliveryWards().find((w) => w.id === this.deliveryWardCode());
    const rate = this.selectedRate();
    if (!province || !district || !ward || !rate) {
      return;
    }
    this.creatingShipment.set(true);
    this.shipmentError.set(null);
    const detailParts = [this.deliveryHamlet(), this.deliveryNeighborhood()].map((p) => p.trim()).filter(Boolean);
    const request: CreateShipmentRequest = {
      // The rate the cashier picked, not just its price: Goship books
      // against this id, so the carrier and cost on the receipt are the
      // ones that were on screen.
      rateId: rate.id,
      toName: this.deliveryName().trim(),
      toPhone: this.deliveryPhone().trim(),
      toAddress: [this.deliveryAddress().trim(), ...detailParts].filter(Boolean).join(', '),
      toCityId: province.id,
      toCityName: province.name,
      toDistrictId: district.id,
      toDistrictName: district.name,
      toWardId: ward.id,
      toWardName: ward.name,
      weightGrams: this.packageWeightGrams(),
      lengthCm: this.packageLengthCm(),
      widthCm: this.packageWidthCm(),
      heightCm: this.packageHeightCm(),
      codAmount: this.codEnabled() ? this.totalAmount() : 0,
      declaredAmount: this.declaredValueEnabled() ? this.totalAmount() : 0,
      senderPaysShipping: this.senderPaysShipping(),
      inspectionPolicy: this.inspectionPolicy(),
      note: `Đơn hàng ${sale.code}${this.deliveryNote().trim() ? ' - ' + this.deliveryNote().trim() : ''}`,
      service: rate.service,
      expected: rate.expected,
      // Ties the parcel to the order this checkout raised, so "Đặt hàng" can
      // show which carrier, which service, what it costs and where it is.
      orderId,
    };
    this.shipmentService.create(request).subscribe({
      next: (res) => {
        this.creatingShipment.set(false);
        this.createdShipment.set(res.shipment);
        this.shipmentService.notifyChanged();
        this.printWhenReceiptRendered();
      },
      error: (err: HttpErrorResponse) => {
        this.creatingShipment.set(false);
        this.shipmentError.set(err.error?.error ?? 'Không tạo được vận đơn.');
        // The sale itself succeeded, so the receipt is still owed - and with
        // the modal gone this is also the only thing that frees the
        // register. The error is kept off the paper (print:hidden in the
        // template); it belongs to the shop, not the customer's copy.
        this.printWhenReceiptRendered();
      },
    });
  }

  private resetDeliveryForm(): void {
    this.deliveryName.set('');
    this.deliveryPhone.set('');
    this.deliveryAddress.set('');
    this.deliveryHamlet.set('');
    this.deliveryNeighborhood.set('');
    this.deliveryNote.set('');
    this.deliveryProvinceId.set('');
    this.deliveryDistrictId.set('');
    this.deliveryWardCode.set('');
    this.deliveryDistricts.set([]);
    this.deliveryWards.set([]);
    this.packageWeightGrams.set(500);
    this.packageLengthCm.set(10);
    this.packageWidthCm.set(10);
    this.packageHeightCm.set(10);
    this.deliveryGatewayTab.set('gateway');
    this.gatewayServiceTab.set('standard');
    this.selectedRateId.set(null);
    this.declaredValueEnabled.set(true);
    this.senderPaysShipping.set(true);
    this.inspectionPolicy.set('NO_INSPECTION');
    this.codEnabled.set(true);
    this.creatingShipment.set(false);
    this.createdShipment.set(null);
    this.shipmentError.set(null);
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
  readonly gridTiles = computed(() => this.gridProducts().map(toTile));
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

  /**
   * Selecting "Chuyển khoản" as the single-tender method should immediately show a
   * VietQR code for THIS sale's amount, the way KiotViet/Bách Hóa Xanh do - not
   * require opening the split-payment dialog and hunting for a "⊞" icon. Zero
   * while any other method (or a split payment) is active, which switchMap below
   * reads as "clear the QR".
   */
  private readonly bankTransferAmount = computed(() =>
    this.selectedMethod() === 'BANK_TRANSFER' && this.splitLines() === null ? this.singleTenderAmount() : 0,
  );

  /** Why the QR couldn't be created (SePay unconfigured, subscription lapsed) - the box shows this instead of spinning forever. */
  readonly bankTransferQrError = signal<string | null>(null);

  /**
   * The open counter QR and everything the SePay webhook has since said
   * about it. Opening a session (rather than fetching a bare image) is what
   * gives the transfer a reference to come back with, so the register can
   * tell that THIS customer paid rather than someone else's transfer landing
   * on the same account - see PosPaymentSessionService.
   *
   * startWith puts the QR on screen immediately instead of one poll later.
   *
   * Knowing the money arrived does NOT finalize the sale on its own: the
   * receipt is only printed when the cashier presses THANH TOÁN, so the
   * register never closes an invoice out from under whoever is standing at
   * it. The paid state on screen is what tells them it is safe to press.
   */
  readonly bankTransferSession = toSignal(
    toObservable(this.bankTransferAmount).pipe(
      debounceTime(300),
      tap(() => this.bankTransferQrError.set(null)),
      switchMap((amount) =>
        amount > 0
          ? this.sepayQrService.createSession(amount).pipe(
              switchMap((session) => this.sepayQrService.watchSession(session).pipe(startWith(session))),
              catchError((err: HttpErrorResponse) => {
                this.bankTransferQrError.set(toActionError(err).message);
                return of(null);
              }),
            )
          : of(null),
      ),
    ),
    { initialValue: null },
  );

  // ---- Save / finalize ----

  readonly submitting = signal(false);
  readonly actionError = signal<ActionError | null>(null);
  readonly completedSale = signal<SaleDTO | null>(null);


  /**
   * The shared "THANH TOÁN" button. In "Bán thường" it opens the payment
   * panel the first time and finalizes the sale once it's open (two clicks).
   * "Bán giao hàng" shows its totals/payment fields inline on the register
   * itself (see the delivery layout in the template), matching KiotViet's
   * own single-screen delivery-sale flow, so it finalizes on the first click.
   */
  primaryAction(): void {
    if (this.lines().length === 0) {
      this.actionError.set({ message: 'Chưa chọn hàng hóa nào.', isUpgradeRequired: false });
      return;
    }
    if (this.saleMode() === 'delivery') {
      if (!this.deliveryFormValid()) {
        this.actionError.set({ message: 'Vui lòng nhập đầy đủ tên, số điện thoại và địa chỉ người nhận.', isUpgradeRequired: false });
        return;
      }
      this.actionError.set(null);
      this.finalizeSale();
      return;
    }
    if (!this.checkoutOpen()) {
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

  /** The delivery panel as the checkout call wants it - see SaleDeliveryRequest. */
  private deliveryRequest(): SaleDeliveryRequest {
    const province = this.deliveryProvinces().find((p) => p.id === this.deliveryProvinceId());
    const district = this.deliveryDistricts().find((d) => d.id === this.deliveryDistrictId());
    const ward = this.deliveryWards().find((w) => w.id === this.deliveryWardCode());
    const detailParts = [this.deliveryHamlet(), this.deliveryNeighborhood()].map((part) => part.trim()).filter(Boolean);
    return {
      recipientName: this.deliveryName().trim(),
      recipientPhone: this.deliveryPhone().trim(),
      address: [this.deliveryAddress().trim(), ...detailParts].filter(Boolean).join(', '),
      provinceName: province?.name ?? null,
      districtName: district?.name ?? null,
      wardName: ward?.name ?? null,
      note: this.deliveryNote().trim() || null,
      codEnabled: this.codEnabled(),
      // Only the gateway tab books a carrier; "Tự giao hàng" is the shop's own
      // legs, and naming a carrier there would put a courier on the order that
      // nobody called.
      carrierName:
        this.deliveryGatewayTab() === 'gateway' ? (this.selectedRate()?.carrierName ?? null) : null,
      // KiotViet asks for a delivery date on its own screen; this register does
      // not, so the order carries none rather than a guessed one.
      expectedDeliveryAt: null,
    };
  }

  private finalizeSale(): void {
    this.submitting.set(true);
    this.actionError.set(null);
    const isDelivery = this.saleMode() === 'delivery';
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
      // COD ("Thu hộ tiền") stands in for the 4-method tender split: the recipient pays the courier on delivery, not the cashier here.
      payments: isDelivery && this.codEnabled() ? [{ method: 'CASH', amount: this.totalAmount() }] : this.paymentRequestLines(),
      // What turns this sale into a row under "Đơn hàng › Đặt hàng": a sale
      // with a recipient and an address is something the shop still owes
      // somebody, so the backend raises an order beside the invoice.
      delivery: isDelivery ? this.deliveryRequest() : null,
    };
    this.saleService.checkout(request).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.completedSale.set(res.sale);
        if (isDelivery && this.deliveryGatewayTab() === 'gateway' && this.selectedRate()) {
          // Printing waits for the shipment: the tracking code belongs on
          // the receipt, and at this moment it still reads "Đang tạo...".
          this.createDeliveryShipment(res.sale, res.orderId);
        } else {
          this.printWhenReceiptRendered();
        }
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  /**
   * The print dialog is the whole of the after-sale UI now: it opens by
   * itself once the sale is saved, and closing it clears the register (see
   * the afterprint listener in the constructor). Every path that completes a
   * sale has to reach here, or the register would sit on a finished sale
   * with nothing left to dismiss it.
   *
   * Deferred rather than called inline: the receipt only enters the DOM on
   * the change-detection pass that follows completedSale being set, so
   * printing in the same tick would capture a page that does not contain
   * it. The wait also lets the store logo decode - Chrome prints an empty
   * box for an image it has not finished loading.
   */
  private printWhenReceiptRendered(): void {
    setTimeout(() => window.print(), 400);
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
    this.resetDeliveryForm();
  }

  exit(): void {
    this.router.navigate(['/dashboard']);
  }
}
