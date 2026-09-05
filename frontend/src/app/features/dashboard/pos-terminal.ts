import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, debounceTime, map, of, switchMap } from 'rxjs';

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
import { CreateGhnShipmentRequest, GhnLocationOption, GhnShipmentDTO } from './ghn-shipment.models';
import { GhnShipmentService } from './ghn-shipment.service';
import { ProductDTO } from './product-admin.models';
import { ProductAdminService } from './product-admin.service';
import { CreateSaleRequest, SALE_PAYMENT_METHOD_LABELS, SaleDTO, SalePaymentMethod, SalePaymentRequest } from './sale.models';
import { SaleService } from './sale.service';
import { SepayQrService } from './sepay-qr.service';
import { SplitPaymentDialog, SplitPaymentLine } from './split-payment-dialog';
import { ActionError, toActionError } from './subscription-error.util';
import { UNIT_AXIS_NAME } from './variant-builder.models';

/** 3 columns x 3 rows per grid page. Deliberately not bigger: the grid sizes each row to its own content (see the auto-rows-min tile grid below) rather than stretching to fill the panel, so a bigger page size just means a bigger blank gap under a sparse catalog - keeping this small keeps a page close to "full" for a modest product count. */
const GRID_PAGE_SIZE = 9;

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
  private readonly sepayQrService = inject(SepayQrService);
  private readonly ghnShipmentService = inject(GhnShipmentService);

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

  // ---- Sale mode: footer tabs (KiotViet's "Bán nhanh" / "Bán thường" / "Bán giao hàng") ----

  readonly saleMode = signal<'normal' | 'delivery'>('normal');

  setSaleMode(mode: 'normal' | 'delivery'): void {
    this.saleMode.set(mode);
    if (mode === 'delivery' && this.deliveryProvinces().length === 0) {
      this.loadDeliveryProvinces();
    }
  }

  // ---- "Bán giao hàng": recipient, address, package, carrier ----
  // Tỉnh/Quận/Phường still cascades through GHN's own master-data endpoints
  // (same as GhnShipmentFormModal) since that's what its real fee/shipment
  // APIs need - GHN's sandbox is genuinely connected on this deployment (see
  // GHN_API_TOKEN/GHN_SHOP_ID). Tổ dân phố/Khu phố are extra, optional detail
  // lines this form also collects (Vietnam's 2025 reform folded these in as
  // informal sub-units once Quận/Huyện was dropped nationwide) - not part of
  // GHN's own address model, just appended to the printed address for extra
  // precision.

  readonly deliveryName = signal('');
  readonly deliveryPhone = signal('');
  readonly deliveryAddress = signal('');
  readonly deliveryHamlet = signal('');
  readonly deliveryNeighborhood = signal('');
  readonly deliveryNote = signal('');

  readonly deliveryProvinceId = signal('');
  readonly deliveryDistrictId = signal('');
  readonly deliveryWardCode = signal('');
  readonly deliveryProvinces = signal<GhnLocationOption[]>([]);
  readonly deliveryDistricts = signal<GhnLocationOption[]>([]);
  readonly deliveryWards = signal<GhnLocationOption[]>([]);
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
    this.ghnShipmentService.provinces().subscribe({
      next: (res) => {
        this.loadingDeliveryProvinces.set(false);
        this.deliveryProvinces.set(res.provinces);
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
    this.ghnShipmentService.districts(provinceId).subscribe({
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
    this.ghnShipmentService.wards(districtId).subscribe({
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
   * "Cổng KiotViet" vs "Tự giao hàng" tabs, matching KiotViet's own screen -
   * only GHN/"Tiêu chuẩn" is a real, selectable integration (this store's GHN
   * sandbox is genuinely connected); the rest render disabled like every
   * other not-yet-wired carrier in this codebase (see delivery-partner.models.ts).
   */
  readonly deliveryGatewayTab = signal<'gateway' | 'self'>('gateway');
  readonly gatewayServiceTab = signal<'standard' | 'priority' | 'fast'>('standard');
  readonly selectedCarrierCode = signal<string | null>('GHN');
  readonly codEnabled = signal(true);

  readonly staticCarrierRows: { code: string; name: string; subtitle: string; badge: string }[] = [
    { code: 'SPX', name: 'SPX - Tiêu chuẩn', subtitle: 'Hỗ trợ đối soát nhanh', badge: 'SPX' },
    { code: 'VTP_ECOD', name: 'VTP - ECOD Hàng nhẹ (<2kg)', subtitle: 'Hỗ trợ đối soát nhanh', badge: 'VTP' },
    { code: 'BEST', name: 'BEST - Express', subtitle: 'Hỗ trợ đối soát nhanh', badge: 'BEST' },
    { code: 'EMS', name: 'EMS - TMĐT', subtitle: 'Thương mại điện tử đồng giá', badge: 'EMS' },
    { code: 'JT', name: 'J&T - Express', subtitle: 'Express', badge: 'J&T' },
    { code: 'VTP_VCBO', name: 'VTP - VCBO Hàng kiện (>5kg)', subtitle: 'Hỗ trợ đối soát nhanh', badge: 'VTP' },
  ];

  setDeliveryGatewayTab(tab: 'gateway' | 'self'): void {
    this.deliveryGatewayTab.set(tab);
  }

  setGatewayServiceTab(tab: 'standard' | 'priority' | 'fast'): void {
    if (tab === 'standard') {
      this.gatewayServiceTab.set(tab);
    }
  }

  selectCarrier(code: string): void {
    if (code === 'GHN') {
      this.selectedCarrierCode.set(code);
    }
  }

  toggleCod(): void {
    this.codEnabled.update((v) => !v);
  }

  /** Live GHN fee quote for the carrier row - recomputed whenever the address or package changes, same debounce pattern as bankTransferQrUrl below. */
  private readonly ghnFeeQuery = computed(() => ({
    districtId: this.deliveryDistrictId(),
    wardCode: this.deliveryWardCode(),
    weight: this.packageWeightGrams(),
    length: this.packageLengthCm(),
    width: this.packageWidthCm(),
    height: this.packageHeightCm(),
  }));

  readonly ghnFeeState = toSignal(
    toObservable(this.ghnFeeQuery).pipe(
      debounceTime(400),
      switchMap((q) => {
        if (!q.districtId || !q.wardCode) {
          return of<{ fee: number | null; error: string | null }>({ fee: null, error: null });
        }
        return this.ghnShipmentService.calculateFee(Number(q.districtId), q.wardCode, q.weight, q.length, q.width, q.height).pipe(
          map((res) => ({ fee: res.fee, error: null })),
          catchError(() => of({ fee: null, error: 'Không thể tính phí GHN.' })),
        );
      }),
    ),
    { initialValue: { fee: null, error: null } },
  );

  readonly ghnFee = computed(() => this.ghnFeeState().fee);

  // ---- GHN shipment creation on checkout (GHN carrier only - see selectCarrier) ----

  readonly creatingShipment = signal(false);
  readonly createdShipment = signal<GhnShipmentDTO | null>(null);
  readonly shipmentError = signal<string | null>(null);

  private createDeliveryShipment(sale: SaleDTO): void {
    const province = this.deliveryProvinces().find((p) => p.id === this.deliveryProvinceId());
    const district = this.deliveryDistricts().find((d) => d.id === this.deliveryDistrictId());
    const ward = this.deliveryWards().find((w) => w.id === this.deliveryWardCode());
    if (!province || !district || !ward) {
      return;
    }
    this.creatingShipment.set(true);
    this.shipmentError.set(null);
    const detailParts = [this.deliveryHamlet(), this.deliveryNeighborhood()].map((p) => p.trim()).filter(Boolean);
    const request: CreateGhnShipmentRequest = {
      toName: this.deliveryName().trim(),
      toPhone: this.deliveryPhone().trim(),
      toAddress: [this.deliveryAddress().trim(), ...detailParts].filter(Boolean).join(', '),
      toProvinceId: Number(province.id),
      toProvinceName: province.name,
      toDistrictId: Number(district.id),
      toDistrictName: district.name,
      toWardCode: ward.id,
      toWardName: ward.name,
      weightGrams: this.packageWeightGrams(),
      lengthCm: this.packageLengthCm(),
      widthCm: this.packageWidthCm(),
      heightCm: this.packageHeightCm(),
      note: `Đơn hàng ${sale.code}${this.deliveryNote().trim() ? ' - ' + this.deliveryNote().trim() : ''}`,
    };
    this.ghnShipmentService.create(request).subscribe({
      next: (res) => {
        this.creatingShipment.set(false);
        this.createdShipment.set(res.shipment);
        this.ghnShipmentService.notifyChanged();
      },
      error: (err: HttpErrorResponse) => {
        this.creatingShipment.set(false);
        this.shipmentError.set(err.error?.error ?? 'Không thể tạo đơn giao hàng GHN.');
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
    this.selectedCarrierCode.set('GHN');
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

  readonly bankTransferQrUrl = toSignal(
    toObservable(this.bankTransferAmount).pipe(
      debounceTime(300),
      switchMap((amount) =>
        amount > 0
          ? this.sepayQrService.getQr(amount).pipe(map((res) => res.qrUrl), catchError(() => of(null)))
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
    };
    this.saleService.checkout(request).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.completedSale.set(res.sale);
        if (isDelivery && this.deliveryGatewayTab() === 'gateway' && this.selectedCarrierCode() === 'GHN') {
          this.createDeliveryShipment(res.sale);
        }
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
    this.resetDeliveryForm();
  }

  exit(): void {
    this.router.navigate(['/dashboard']);
  }
}
