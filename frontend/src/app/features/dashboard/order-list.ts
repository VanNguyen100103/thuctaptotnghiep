import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { StoreProfile } from '../../core/store/store-profile.models';
import { StoreProfileService } from '../../core/store/store-profile.service';
import { ActionMenu, ActionMenuItem } from './action-menu';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { ColumnDef, ColumnPicker } from './column-picker';
import { exportRowsToCsv } from './csv-export.util';
import { loadColumnPrefs, saveColumnPrefs } from './column-prefs.util';
import { INTEGRATED_CARRIERS } from './delivery-partner.models';
import { FilterMultiselect, FilterOption } from './filter-multiselect';
import { FilterSelect } from './filter-select';
import { OrderBulkEdit, OrderBulkEditModal } from './order-bulk-edit-modal';
import { OrderDetailPanel } from './order-detail-panel';
import {
  BulkOrderResult,
  DEFAULT_ORDER_STATUSES,
  DeliveryArea,
  MERGEABLE_ORDER_STATUSES,
  ORDER_PAYMENT_FILTER_OPTIONS,
  ORDER_STATUS_FILTERS,
  ORDER_STATUS_LABELS,
  SALES_CHANNELS,
  SALES_CHANNEL_LABELS,
  SalesChannel,
  StoreOrderDTO,
  StoreOrderDeliveryDTO,
  StoreOrderPage,
  StoreOrderStatus,
} from './order.models';
import { OrderService } from './order.service';
import { SearchField, SearchPanel, SearchValues } from './search-panel';
import { ActionError, toActionError } from './subscription-error.util';
import { TIME_PRESETS, TimeMode, TimePreset, formatIsoDate, presetRange } from './time-filter.util';

const COLUMNS: ColumnDef[] = [
  { key: 'code', label: 'Mã đặt hàng' },
  { key: 'createdAt', label: 'Thời gian' },
  { key: 'customerCode', label: 'Mã KH' },
  { key: 'customerName', label: 'Khách hàng' },
  { key: 'customerPhone', label: 'Điện thoại' },
  { key: 'recipient', label: 'Người nhận' },
  { key: 'channel', label: 'Kênh bán' },
  { key: 'subtotal', label: 'Tổng tiền hàng' },
  { key: 'discount', label: 'Giảm giá' },
  { key: 'otherCollection', label: 'Thu khác' },
  { key: 'shipping', label: 'Phí giao hàng' },
  { key: 'deliveryFee', label: 'Phí trả đối tác' },
  { key: 'deliveryService', label: 'Dịch vụ giao hàng' },
  { key: 'total', label: 'Khách cần trả' },
  { key: 'paid', label: 'Khách đã trả' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'carrier', label: 'Đối tác giao hàng' },
  { key: 'tracking', label: 'Mã vận đơn' },
  { key: 'expectedDelivery', label: 'Thời gian giao hàng' },
  { key: 'address', label: 'Địa chỉ giao' },
  { key: 'creator', label: 'Người tạo' },
  { key: 'saleCode', label: 'Mã hóa đơn' },
  { key: 'note', label: 'Ghi chú' },
];

const DEFAULT_COLUMNS = ['code', 'createdAt', 'customerCode', 'customerName', 'total', 'paid', 'status'];

const COLUMN_STORAGE_KEY = 'tryum.order-list.columns';

/**
 * "GHTK - Tiết kiệm", or just the carrier when no booking exists to name a
 * service. An order the shop delivers on its own legs has neither.
 */
function deliveryServiceLabel(order: StoreOrderDTO): string {
  const delivery = order.delivery;
  const carrier = delivery?.carrierShortName || delivery?.carrierName || order.shippingCarrier;
  if (!carrier) {
    return '';
  }
  return delivery?.service ? `${carrier} - ${delivery.service}` : carrier;
}

/**
 * "Đặt hàng" - what the shop still owes somebody: an order a customer placed
 * on the storefront, or a "Bán giao hàng" sale rung up at the register, which
 * writes an order beside its invoice (see SaleService#createDeliveryOrder).
 *
 * Laid out the way KiotViet lays out its own order list: filters down the
 * left, the list in the middle, a clicked row expanding into its detail right
 * there rather than navigating away - and a toolbar that changes the moment a
 * row is ticked, from "add something" to "do something to these".
 */
@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    VndCurrencyPipe,
    ActionMenu,
    FilterMultiselect,
    FilterSelect,
    ColumnPicker,
    SearchPanel,
    OrderBulkEditModal,
    OrderDetailPanel,
  ],
  templateUrl: './order-list.html',
})
export class OrderList {
  private readonly orderService = inject(OrderService);
  private readonly storeProfileService = inject(StoreProfileService);

  readonly statusLabels = ORDER_STATUS_LABELS;
  readonly channelLabels = SALES_CHANNEL_LABELS;

  /** Drives the "Trạng thái" chip box. */
  readonly statusOptions: FilterOption[] = ORDER_STATUS_FILTERS.map((status) => ({
    value: status,
    label: ORDER_STATUS_LABELS[status],
  }));

  readonly timePresets = TIME_PRESETS;
  readonly timePresetOptions = TIME_PRESETS.map((preset) => ({ value: preset.value, label: preset.label }));

  /** "Chi nhánh" - the same one-branch box the Hóa đơn screen shows; see invoice-list.ts. */
  readonly branchOptions: FilterOption[] = [{ value: 'main', label: 'Chi nhánh trung tâm' }];
  readonly branches = signal<string[]>(['main']);

  /**
   * "Phương thức thanh toán". The list spans both halves of the shop because
   * this screen does: a storefront order settles through a gateway, a register
   * one through a till tender.
   */
  readonly paymentMethodOptions: FilterOption[] = ORDER_PAYMENT_FILTER_OPTIONS;
  readonly paymentMethods = signal<string[]>([]);

  /** "Đối tác giao hàng" - the carriers KiotViet ships integrations for, same list as the Giao hàng screen. */
  readonly carrierOptions: FilterOption[] = INTEGRATED_CARRIERS.map((carrier) => ({
    value: carrier.name,
    label: carrier.name,
  }));
  readonly carriers = signal<string[]>([]);

  /** "Kênh bán". */
  readonly channelOptions: FilterOption[] = SALES_CHANNELS.map((channel) => ({
    value: channel,
    label: SALES_CHANNEL_LABELS[channel],
  }));
  readonly channels = signal<string[]>([]);

  /**
   * "Người tạo" - only the staff who have actually raised an order here, so
   * the box cannot offer a name that comes back empty. A failed call leaves it
   * empty rather than breaking the screen; the list still loads without it.
   */
  private readonly creatorNames = toSignal(
    this.orderService.creators().pipe(
      map((response) => response.creators),
      catchError(() => of<string[]>([])),
    ),
    { initialValue: [] as string[] },
  );

  readonly creatorOptions = computed<FilterOption[]>(() =>
    this.creatorNames().map((name) => ({ value: name, label: name })),
  );
  readonly creators = signal<string[]>([]);

  /**
   * "Khu vực giao hàng" - built from where this store has actually shipped
   * rather than from a national address list: a sidebar offering all 63
   * provinces to a shop that delivers within one district is a longer list
   * saying less.
   */
  private readonly deliveryAreaList = toSignal(
    this.orderService.deliveryAreas().pipe(
      map((response) => response.areas),
      catchError(() => of<DeliveryArea[]>([])),
    ),
    { initialValue: [] as DeliveryArea[] },
  );

  readonly province = signal('');
  readonly district = signal('');

  readonly provinceOptions = computed(() => [
    { value: '', label: 'Chọn Tỉnh/TP' },
    ...this.deliveryAreaList().map((area) => ({ value: area.province, label: area.province })),
  ]);

  readonly districtOptions = computed(() => {
    const area = this.deliveryAreaList().find((a) => a.province === this.province());
    return [
      { value: '', label: 'Chọn Quận/Huyện' },
      ...(area?.districts ?? []).map((name) => ({ value: name, label: name })),
    ];
  });

  onProvinceChanged(value: string): void {
    this.province.set(value);
    // A district only means something under its province, so it cannot survive one changing.
    this.district.set('');
    this.page.set(0);
  }

  onDistrictChanged(value: string): void {
    this.district.set(value);
    this.page.set(0);
  }

  onPaymentMethodsChanged(values: string[]): void {
    this.paymentMethods.set(values);
    this.page.set(0);
  }

  onCarriersChanged(values: string[]): void {
    this.carriers.set(values);
    this.page.set(0);
  }

  onChannelsChanged(values: string[]): void {
    this.channels.set(values);
    this.page.set(0);
  }

  onCreatorsChanged(values: string[]): void {
    this.creators.set(values);
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

  /** How many columns an expanded detail row has to span - the tick and star columns are always drawn. */
  readonly columnCount = computed(() => this.visibleColumns().length + 2);

  // ---- selection: what the toolbar acts on ----

  readonly selectedIds = signal<number[]>([]);

  readonly selectedCount = computed(() => this.selectedIds().length);

  isSelected(id: number): boolean {
    return this.selectedIds().includes(id);
  }

  toggleRowSelection(id: number, event: Event): void {
    // The tick lives inside a row that expands on click; without this, ticking
    // a box would also open the detail panel under it.
    event.stopPropagation();
    this.selectedIds.update((ids) => (ids.includes(id) ? ids.filter((i) => i !== id) : [...ids, id]));
  }

  /** The rows on screen - "select all" means this page, which is what the box sits above. */
  private readonly pageOrderIds = computed(() => (this.pageState().data?.orders ?? []).map((order) => order.id));

  readonly allOnPageSelected = computed(() => {
    const ids = this.pageOrderIds();
    return ids.length > 0 && ids.every((id) => this.selectedIds().includes(id));
  });

  readonly someOnPageSelected = computed(() => {
    const ids = this.pageOrderIds();
    return ids.some((id) => this.selectedIds().includes(id)) && !this.allOnPageSelected();
  });

  toggleAllOnPage(): void {
    const ids = this.pageOrderIds();
    if (this.allOnPageSelected()) {
      this.selectedIds.update((selected) => selected.filter((id) => !ids.includes(id)));
    } else {
      this.selectedIds.update((selected) => [...new Set([...selected, ...ids])]);
    }
  }

  clearSelection(): void {
    this.selectedIds.set([]);
  }

  /** The ticked rows, in the order they are listed - every bulk action reads this. */
  readonly selectedOrders = computed<StoreOrderDTO[]>(() =>
    (this.pageState().data?.orders ?? []).filter((order) => this.selectedIds().includes(order.id)),
  );

  // ---- the star column ----

  /**
   * Stars already saved this session. Held apart from the fetched rows so a
   * star does not cost a refetch of the whole list; an entry only ever exists
   * once the server has accepted it, so it cannot disagree with what is stored.
   */
  private readonly starOverrides = signal<Record<number, boolean>>({});

  isStarred(order: StoreOrderDTO): boolean {
    return this.starOverrides()[order.id] ?? order.starred;
  }

  toggleStar(order: StoreOrderDTO, event: Event): void {
    event.stopPropagation();
    const next = !this.isStarred(order);
    this.orderService.setStarred(order.id, next).subscribe({
      next: () => this.starOverrides.update((map) => ({ ...map, [order.id]: next })),
      error: (err) => this.actionError.set(toActionError(err)),
    });
  }

  // ---- toolbar actions ----

  readonly actionError = signal<ActionError | null>(null);
  readonly actionMessage = signal<string | null>(null);
  readonly busy = signal(false);

  dismissAction(): void {
    this.actionError.set(null);
    this.actionMessage.set(null);
  }

  /** "Xuất file ▾" - KiotViet offers the list and the lines behind the same button. */
  readonly exportMenuItems: ActionMenuItem[] = [
    { key: 'list', label: 'Xuất file danh sách đặt hàng' },
    { key: 'detail', label: 'Xuất file chi tiết đặt hàng' },
  ];

  /**
   * The "..." menu. Every entry acts on the ticked rows, so the labels are
   * KiotViet's own; what each one means here is spelled out in the handlers.
   */
  readonly bulkMenuItems: ActionMenuItem[] = [
    { key: 'edit', label: 'Sửa người nhận đặt, kênh bán, ghi chú' },
    { key: 'process', label: 'Xử lý đặt hàng' },
    { key: 'finish', label: 'Kết thúc', danger: true, separatorBefore: true },
    { key: 'cancel', label: 'Hủy đơn', danger: true },
  ];

  onExportPicked(key: string): void {
    if (key === 'detail') {
      this.exportDetailCsv();
    } else {
      this.exportCsv();
    }
  }

  onBulkPicked(key: string): void {
    switch (key) {
      case 'edit':
        this.bulkEditOpen.set(true);
        break;
      case 'process':
        // "Xử lý đặt hàng" - the shop has started packing it.
        this.applyBulkStatus('PROCESSING', 'Đã chuyển sang xử lý');
        break;
      case 'finish':
        // "Kết thúc" - the order is done with; DELIVERED is this system's
        // terminal success state, and it is what settles a COD payment.
        this.applyBulkStatus('DELIVERED', 'Đã kết thúc');
        break;
      case 'cancel':
        this.bulkCancel();
        break;
    }
  }

  private reportBulk(result: BulkOrderResult, verb: string): void {
    const skipped = Object.entries(result.skipped);
    const parts = [`${verb} ${result.updated} đơn.`];
    if (skipped.length > 0) {
      parts.push(
        `Bỏ qua ${skipped.length} đơn: ` + skipped.map(([code, reason]) => `${code} (${reason})`).join('; '),
      );
    }
    this.actionMessage.set(parts.join(' '));
    this.busy.set(false);
    this.orderService.notifyChanged();
    // The rows that moved may no longer match the filters, so a selection kept
    // here would act on ids that are not on screen any more.
    this.clearSelection();
  }

  private applyBulkStatus(status: StoreOrderStatus, verb: string): void {
    const ids = this.selectedIds();
    if (ids.length === 0 || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.actionMessage.set(null);
    this.orderService.bulkStatus(ids, status).subscribe({
      next: (result) => this.reportBulk(result, verb),
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  private bulkCancel(): void {
    const ids = this.selectedIds();
    if (ids.length === 0 || this.busy()) {
      return;
    }
    const reason = window.prompt(`Lý do hủy ${ids.length} đơn đặt hàng:`);
    if (reason === null) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.actionMessage.set(null);
    this.orderService.bulkCancel(ids, reason.trim()).subscribe({
      next: (result) => this.reportBulk(result, 'Đã hủy'),
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  // ---- "Gộp đơn" ----

  /**
   * Two rows minimum, one customer, and nothing settled yet - the same three
   * rules the backend enforces, checked here so the button can say why it is
   * off instead of failing on click.
   */
  readonly mergeBlockedReason = computed<string | null>(() => {
    const orders = this.selectedOrders();
    if (orders.length < 2) {
      return 'Chọn ít nhất 2 đơn đặt hàng để gộp';
    }
    const unsettled = orders.every((order) => MERGEABLE_ORDER_STATUSES.includes(order.status));
    if (!unsettled) {
      return 'Chỉ gộp được đơn chưa thanh toán';
    }
    if (orders.some((order) => !order.customerCode)) {
      return 'Không gộp được đơn không có khách hàng';
    }
    const buyers = new Set(orders.map((order) => order.customerCode));
    if (buyers.size > 1) {
      return 'Chỉ gộp được các đơn của cùng một khách hàng';
    }
    return null;
  });

  mergeOrders(): void {
    if (this.mergeBlockedReason() || this.busy()) {
      return;
    }
    const ids = this.selectedIds();
    if (!window.confirm(`Gộp ${ids.length} đơn đặt hàng thành một đơn mới?`)) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.actionMessage.set(null);
    this.orderService.merge(ids).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.actionMessage.set(result.message);
        this.orderService.notifyChanged();
        this.clearSelection();
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  // ---- "Sửa người nhận đặt, kênh bán, ghi chú" ----

  readonly bulkEditOpen = signal(false);

  onBulkEditApplied(change: OrderBulkEdit): void {
    const ids = this.selectedIds();
    if (ids.length === 0) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.actionMessage.set(null);
    this.orderService.bulkUpdate(ids, change).subscribe({
      next: (result) => {
        this.bulkEditOpen.set(false);
        this.reportBulk(result, 'Đã cập nhật');
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  // ---- "In" ----

  readonly store = signal<StoreProfile | null>(null);
  readonly printOrders = signal<StoreOrderDTO[]>([]);
  readonly printedAt = signal<Date | null>(null);

  constructor() {
    this.storeProfileService.getCurrentStore().subscribe({
      next: (store) => this.store.set(store),
      // The slip prints without a shop header rather than not printing at all.
      error: () => this.store.set(null),
    });
    // Both the body class and the slips are global state this screen turned
    // on; leaving either behind would hide the next screen the shop prints.
    const afterPrint = () => this.endPrinting();
    window.addEventListener('afterprint', afterPrint);
    inject(DestroyRef).onDestroy(() => {
      window.removeEventListener('afterprint', afterPrint);
      this.endPrinting();
    });
  }

  private endPrinting(): void {
    document.body.classList.remove('printing-orders');
    this.printOrders.set([]);
  }

  /**
   * "In" - one order slip per ticked row.
   *
   * The details are fetched first: a list row carries no line items, and a
   * slip without them is a piece of paper nobody in the packing area can use.
   */
  printSelected(): void {
    const ids = this.selectedIds();
    if (ids.length === 0 || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    forkJoin(ids.map((id) => this.orderService.getById(id))).subscribe({
      next: (orders) => {
        this.busy.set(false);
        this.printOrders.set(orders);
        this.printedAt.set(new Date());
        document.body.classList.add('printing-orders');
        // The slips only enter the DOM on the change-detection pass that
        // follows, so printing in this tick would capture a page without them.
        setTimeout(() => window.print(), 200);
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  printQuantity(order: StoreOrderDTO): number {
    return (order.items ?? []).reduce((sum, item) => sum + item.quantity, 0);
  }

  // ---- "Xuất file" ----

  readonly exporting = signal(false);

  /**
   * The rows an export or a print covers: the ticked ones when there are any,
   * everything the filters match when there are not. That is KiotViet's rule,
   * and it is the one that matches what the shop just did with its mouse.
   */
  private exportQuery(size: number) {
    const range = this.dateRange();
    const deliveryRange = this.deliveryRange();
    const search = this.search();
    return {
      statuses: this.statuses(),
      from: range.from,
      to: range.to,
      code: (search['code'] ?? '').trim(),
      product: (search['product'] ?? '').trim(),
      customer: (search['customer'] ?? '').trim(),
      tracking: (search['tracking'] ?? '').trim(),
      note: (search['note'] ?? '').trim(),
      paymentMethods: this.paymentMethods(),
      carriers: this.carriers(),
      channels: this.channels(),
      creators: this.creators(),
      province: this.province() || null,
      district: this.district() || null,
      deliveryFrom: deliveryRange.from,
      deliveryTo: deliveryRange.to,
      page: 0,
      size,
    };
  }

  /** "Xuất file danh sách đặt hàng" - one row per order, in the columns on screen. */
  exportCsv(): void {
    const result = this.pageState().data;
    if (!result || result.totalItems === 0 || this.exporting()) {
      return;
    }
    const selected = this.selectedOrders();
    const keys = this.visibleColumns();
    const headers = keys.map((key) => COLUMNS.find((c) => c.key === key)?.label ?? key);
    const write = (orders: StoreOrderDTO[]) =>
      exportRowsToCsv(headers, orders.map((order) => keys.map((key) => this.cellValue(order, key))), 'dat-hang.csv');

    if (selected.length > 0) {
      write(selected);
      return;
    }
    this.exporting.set(true);
    this.orderService.list(this.exportQuery(result.totalItems)).subscribe({
      next: (page) => {
        write(page.orders);
        this.exporting.set(false);
      },
      error: (err) => {
        this.exporting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  /**
   * "Xuất file chi tiết đặt hàng" - one row per line, the order's own columns
   * repeated beside each. Every order has to be fetched for its lines, which
   * is a request each; fine at this app's scale, the same trade the list
   * endpoint already makes by summing the whole match set in memory.
   */
  exportDetailCsv(): void {
    const result = this.pageState().data;
    if (!result || result.totalItems === 0 || this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.actionError.set(null);
    const selected = this.selectedIds();
    const ids$ =
      selected.length > 0
        ? of(selected)
        : this.orderService.list(this.exportQuery(result.totalItems)).pipe(
            map((page) => page.orders.map((order) => order.id)),
          );

    ids$
      .pipe(switchMap((ids) => (ids.length === 0 ? of([]) : forkJoin(ids.map((id) => this.orderService.getById(id))))))
      .subscribe({
        next: (orders) => {
          const headers = [
            'Mã đặt hàng',
            'Thời gian',
            'Khách hàng',
            'Trạng thái',
            'Mã hàng',
            'Tên hàng',
            'Thuộc tính',
            'Số lượng',
            'Đơn giá',
            'Giảm giá',
            'Thành tiền',
          ];
          const rows: string[][] = [];
          for (const order of orders) {
            for (const item of order.items ?? []) {
              rows.push([
                order.code,
                new Date(order.createdAt).toLocaleString('vi-VN'),
                order.customerName ?? 'Khách lẻ',
                ORDER_STATUS_LABELS[order.status],
                item.productSku ?? '',
                item.productName,
                item.variantLabel ?? '',
                String(item.quantity),
                String(item.unitPrice),
                String(item.discountAmount),
                String(item.lineTotal),
              ]);
            }
          }
          exportRowsToCsv(headers, rows, 'dat-hang-chi-tiet.csv');
          this.exporting.set(false);
        },
        error: (err) => {
          this.exporting.set(false);
          this.actionError.set(toActionError(err));
        },
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
      case 'recipient':
        return order.recipientName ?? '';
      case 'channel':
        return SALES_CHANNEL_LABELS[order.salesChannel] ?? order.salesChannel;
      case 'subtotal':
        return String(order.subtotal);
      case 'discount':
        return String(order.discountAmount);
      case 'otherCollection':
        return String(order.otherCollectionAmount);
      case 'shipping':
        return String(order.shippingCost);
      case 'deliveryFee':
        return order.delivery ? String(order.delivery.shippingFee) : '';
      case 'deliveryService':
        return deliveryServiceLabel(order);
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
      case 'expectedDelivery':
        return order.expectedDeliveryAt ? new Date(order.expectedDeliveryAt).toLocaleString('vi-VN') : '';
      case 'address':
        return order.shippingAddress ?? '';
      case 'creator':
        return order.createdBy ?? '';
      case 'saleCode':
        return order.saleCode ?? '';
      case 'note':
        return order.notes ?? '';
      default:
        return '';
    }
  }

  // ---- the expanded detail row ----

  readonly expandedId = signal<number | null>(null);

  toggleExpanded(id: number): void {
    this.expandedId.update((current) => (current === id ? null : id));
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

  /**
   * "Thời gian giao hàng" - the date the shop promised, which is a different
   * question from when the order came in, so it gets its own pair of controls.
   */
  readonly deliveryTimeMode = signal<TimeMode>('preset');
  readonly deliveryPreset = signal<TimePreset>('all');
  readonly deliveryCustomFrom = signal<string>('');
  readonly deliveryCustomTo = signal<string>('');
  readonly deliveryPickerOpen = signal(false);

  private readonly deliveryRange = computed<{ from: string | null; to: string | null }>(() =>
    this.deliveryTimeMode() === 'custom'
      ? { from: this.deliveryCustomFrom() || null, to: this.deliveryCustomTo() || null }
      : presetRange(this.deliveryPreset()),
  );

  readonly deliveryRangeText = computed(() => {
    const from = this.deliveryCustomFrom();
    const to = this.deliveryCustomTo();
    if (!from && !to) {
      return null;
    }
    return `${from ? formatIsoDate(from) : '...'} - ${to ? formatIsoDate(to) : '...'}`;
  });

  setDeliveryTimeMode(mode: TimeMode): void {
    this.deliveryTimeMode.set(mode);
    this.page.set(0);
  }

  setDeliveryPreset(value: string): void {
    this.deliveryPreset.set(value as TimePreset);
    this.deliveryTimeMode.set('preset');
    this.deliveryPickerOpen.set(false);
    this.page.set(0);
  }

  toggleDeliveryPicker(): void {
    this.deliveryPickerOpen.update((open) => !open);
    this.setDeliveryTimeMode('custom');
  }

  onDeliveryFromChange(event: Event): void {
    this.deliveryCustomFrom.set((event.target as HTMLInputElement).value);
    this.page.set(0);
  }

  onDeliveryToChange(event: Event): void {
    this.deliveryCustomTo.set((event.target as HTMLInputElement).value);
    this.page.set(0);
  }

  readonly pageState = toSignal(
    toObservable(
      computed(() => ({
        statuses: this.statuses(),
        range: this.dateRange(),
        deliveryRange: this.deliveryRange(),
        search: this.search(),
        methods: this.paymentMethods(),
        carriers: this.carriers(),
        channels: this.channels(),
        creators: this.creators(),
        province: this.province(),
        district: this.district(),
        page: this.page(),
        size: this.pageSize(),
        tick: this.orderService.changed(),
      })),
    ).pipe(
      switchMap((query) =>
        toApiState<StoreOrderPage>(
          this.orderService.list({
            statuses: query.statuses,
            from: query.range.from,
            to: query.range.to,
            code: (query.search['code'] ?? '').trim(),
            product: (query.search['product'] ?? '').trim(),
            customer: (query.search['customer'] ?? '').trim(),
            tracking: (query.search['tracking'] ?? '').trim(),
            note: (query.search['note'] ?? '').trim(),
            paymentMethods: query.methods,
            carriers: query.carriers,
            channels: query.channels,
            creators: query.creators,
            province: query.province || null,
            district: query.district || null,
            deliveryFrom: query.deliveryRange.from,
            deliveryTo: query.deliveryRange.to,
            page: query.page,
            size: query.size,
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

  /** Ticks every status box, so the hidden orders come back into view. */
  showAllStatuses(): void {
    this.onStatusesChanged(ORDER_STATUS_FILTERS);
  }

  onStatusesChanged(values: string[]): void {
    this.statuses.set(values as StoreOrderStatus[]);
    this.page.set(0);
  }

  setTimeMode(mode: TimeMode): void {
    this.timeMode.set(mode);
    this.page.set(0);
  }

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
    this.clearSelection();
    this.pageSize.set(Number((event.target as HTMLSelectElement).value));
    this.page.set(0);
  }

  /**
   * Paging drops the selection.
   *
   * Every guard on the toolbar - can these be merged, whose orders are these -
   * reads the rows on screen, because a list row is the only place their
   * details exist. A selection carried onto the next page would therefore be
   * vouched for by looking at rows it does not contain. KiotViet does carry
   * one across pages; this trades that for a toolbar that can always answer
   * for what it is about to do.
   */
  private goToPage(index: number): void {
    if (index === this.page()) {
      return;
    }
    this.clearSelection();
    this.page.set(index);
  }

  firstPage(): void {
    this.goToPage(0);
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.goToPage(this.page() - 1);
    }
  }

  nextPage(): void {
    const totalPages = this.pageState().data?.totalPages ?? 1;
    if (this.page() < totalPages - 1) {
      this.goToPage(this.page() + 1);
    }
  }

  lastPage(): void {
    const totalPages = this.pageState().data?.totalPages ?? 1;
    this.goToPage(Math.max(0, totalPages - 1));
  }

  channelLabel(channel: SalesChannel): string {
    return SALES_CHANNEL_LABELS[channel] ?? channel;
  }

  deliveryServiceLabel(order: StoreOrderDTO): string {
    return deliveryServiceLabel(order);
  }

  /** "Người gửi trả phí" vs the courier collecting it at the door. */
  feePayerLabel(delivery: StoreOrderDeliveryDTO): string {
    return delivery.senderPaysShipping ? 'Người gửi trả phí' : 'Người nhận trả phí';
  }

  /**
   * Colour by what the status means for the shop, not one hue per value.
   * There are twenty-two of them now and nobody can hold twenty-two colours
   * apart; four bands can be read across a list at a glance - waiting on
   * somebody (amber), on the move (blue), arrived (green), went wrong (red).
   */
  statusBadgeClass(status: StoreOrderStatus): string {
    const base = 'rounded-full px-2 py-0.5 text-[11px] font-medium';
    switch (status) {
      case 'PENDING':
      case 'PAYMENT_PENDING':
      case 'PENDING_COD':
      case 'AWAITING_PICKUP':
        return `${base} bg-amber-100 text-amber-700`;
      case 'PAID':
      case 'PROCESSING':
      case 'PICKING':
      case 'PICKED_UP':
      case 'AT_WAREHOUSE':
      case 'IN_TRANSIT':
      case 'SHIPPED':
        return `${base} bg-blue-100 text-blue-700`;
      case 'DELIVERED':
      case 'COD_SETTLEMENT':
      case 'COMPLETED':
        return `${base} bg-green-100 text-green-700`;
      // Not endings, but not going well either - the shop should notice these.
      case 'DELIVERY_FAILED':
      case 'PARTIALLY_DELIVERED':
      case 'RETURNING':
        return `${base} bg-orange-100 text-orange-700`;
      case 'RETURNED':
      case 'LOST':
      case 'FAILED':
        return `${base} bg-red-100 text-red-700`;
      default:
        return `${base} bg-gray-100 text-gray-600`;
    }
  }
}
