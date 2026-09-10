import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { ActionMenu, ActionMenuItem } from './action-menu';
import { toApiState } from './api-state.util';
import { ColumnDef, ColumnPicker } from './column-picker';
import { loadColumnPrefs, saveColumnPrefs } from './column-prefs.util';
import { exportRowsToCsv } from './csv-export.util';
import { FilterCheckboxGroup } from './filter-checkbox-group';
import { FilterMultiselect, FilterOption } from './filter-multiselect';
import { FilterSelect, SelectOption } from './filter-select';
import { PurchaseOrderForm } from './purchase-order-form';
import {
  PurchaseOrderDTO,
  PurchaseOrderListQuery,
  PurchaseOrderPage,
  PurchaseOrderStatus,
} from './purchase-order.models';
import { PurchaseOrderService } from './purchase-order.service';
import { SearchField, SearchPanel, SearchValues } from './search-panel';
import { ActionError, toActionError } from './subscription-error.util';
import { TIME_PRESETS, TimeMode, TimePreset, formatIsoDate, presetRange } from './time-filter.util';

/**
 * The columns KiotViet's Nhập hàng table can show. Its own list also offers
 * "Mã đặt hàng nhập" and "VAT nhập hàng"; neither is here, because neither
 * exists in this app - Đặt hàng nhập and input-VAT are the two pieces of the
 * Mua hàng tab TODO.md leaves out, and a column that can only ever print a
 * dash is a wider table saying nothing.
 */
const COLUMNS: ColumnDef[] = [
  { key: 'code', label: 'Mã nhập hàng' },
  { key: 'createdAt', label: 'Thời gian' },
  { key: 'supplierCode', label: 'Mã NCC' },
  { key: 'supplierName', label: 'Nhà cung cấp' },
  { key: 'totalGoodsValue', label: 'Tổng tiền hàng' },
  { key: 'discount', label: 'Giảm giá' },
  { key: 'supplierCharge', label: 'Chi phí nhập trả NCC' },
  { key: 'payable', label: 'Cần trả NCC' },
  { key: 'paid', label: 'Tiền đã trả NCC' },
  { key: 'debt', label: 'Tính vào công nợ' },
  { key: 'otherCosts', label: 'Chi phí nhập khác' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'creator', label: 'Người tạo' },
  { key: 'receiver', label: 'Người nhập' },
  { key: 'note', label: 'Ghi chú' },
];

/** What KiotViet's own Nhập hàng list shows before anyone touches the column picker. */
const DEFAULT_COLUMNS = ['code', 'createdAt', 'supplierCode', 'supplierName', 'payable', 'status'];

const COLUMN_STORAGE_KEY = 'tryum.purchase-order-list.columns';

const STATUS_LABELS: Record<PurchaseOrderStatus, string> = {
  DRAFT: 'Phiếu tạm',
  COMPLETED: 'Đã nhập hàng',
  CANCELLED: 'Đã hủy',
};

@Component({
  selector: 'app-purchase-order-list',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    VndCurrencyPipe,
    ActionMenu,
    ColumnPicker,
    FilterCheckboxGroup,
    FilterMultiselect,
    FilterSelect,
    PurchaseOrderForm,
    SearchPanel,
  ],
  templateUrl: './purchase-order-list.html',
})
export class PurchaseOrderList {
  private readonly purchaseOrderService = inject(PurchaseOrderService);

  /** Clicking a row expands its detail inline, right there in the list - matches KiotViet's own list (only "+ Nhập hàng" opens a separate page). */
  readonly selectedId = signal<number | null>(null);

  toggleSelect(id: number): void {
    this.selectedId.update((current) => (current === id ? null : id));
  }

  // ---- sidebar filters ----

  /** "Chi nhánh" - the same one-branch box the Hóa đơn and Đặt hàng screens show. */
  readonly branchOptions: FilterOption[] = [{ value: 'main', label: 'Chi nhánh trung tâm' }];
  readonly branches = signal<string[]>(['main']);

  readonly statusOptions: FilterOption[] = [
    { value: 'DRAFT', label: STATUS_LABELS.DRAFT },
    { value: 'COMPLETED', label: STATUS_LABELS.COMPLETED },
    { value: 'CANCELLED', label: STATUS_LABELS.CANCELLED },
  ];

  /** Defaults match KiotViet's own list screen and the backend's default filter. */
  readonly statuses = signal<string[]>(['DRAFT', 'COMPLETED']);

  onStatusesChanged(values: string[]): void {
    this.statuses.set(values);
    this.page.set(0);
  }

  readonly timePresetOptions: SelectOption[] = TIME_PRESETS.map((preset) => ({
    value: preset.value,
    label: preset.label,
  }));

  readonly timeMode = signal<TimeMode>('preset');
  readonly timePreset = signal<TimePreset>('this-month');
  readonly customFrom = signal<string>('');
  readonly customTo = signal<string>('');

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

  private readonly dateRange = computed<{ from: string | null; to: string | null }>(() =>
    this.timeMode() === 'custom'
      ? { from: this.customFrom() || null, to: this.customTo() || null }
      : presetRange(this.timePreset()),
  );

  setTimeMode(mode: TimeMode): void {
    this.timeMode.set(mode);
    this.page.set(0);
  }

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
    this.setTimeMode('custom');
  }

  onCustomToChange(event: Event): void {
    this.customTo.set((event.target as HTMLInputElement).value);
    this.setTimeMode('custom');
  }

  /**
   * "Người tạo" / "Người nhập" - only the staff who have actually raised or
   * received a receipt, so neither box can offer a name that comes back
   * empty. A failed call leaves just "Tất cả" rather than breaking the
   * screen; the list still loads without it.
   */
  private readonly people = toSignal(
    this.purchaseOrderService.people().pipe(
      catchError(() => of({ creators: [] as string[], receivers: [] as string[] })),
    ),
    { initialValue: { creators: [] as string[], receivers: [] as string[] } },
  );

  private static toOptions(names: string[]): SelectOption[] {
    return [{ value: '', label: 'Tất cả' }, ...names.map((name) => ({ value: name, label: name }))];
  }

  readonly creatorOptions = computed(() => PurchaseOrderList.toOptions(this.people().creators));
  readonly receiverOptions = computed(() => PurchaseOrderList.toOptions(this.people().receivers));

  /** '' is KiotViet's "Tất cả". */
  readonly createdBy = signal('');
  readonly completedBy = signal('');

  setCreatedBy(value: string): void {
    this.createdBy.set(value);
    this.page.set(0);
  }

  setCompletedBy(value: string): void {
    this.completedBy.set(value);
    this.page.set(0);
  }

  // ---- the search box and the panel behind its sliders icon ----

  readonly searchFields: SearchField[] = [
    { key: 'code', placeholder: 'Theo mã phiếu nhập' },
    { key: 'product', placeholder: 'Theo mã, tên hàng' },
    { key: 'supplier', placeholder: 'Theo mã, tên nhà cung cấp' },
    { key: 'note', placeholder: 'Theo ghi chú' },
  ];

  readonly search = signal<SearchValues>({ code: '', product: '', supplier: '', note: '' });

  onSearchApplied(values: SearchValues): void {
    this.search.set(values);
    this.page.set(0);
  }

  // ---- columns ----

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

  // ---- paging ----

  readonly page = signal(0);
  readonly pageSize = signal(15);

  private listQuery(page: number, size: number): PurchaseOrderListQuery {
    const range = this.dateRange();
    const search = this.search();
    return {
      statuses: this.statuses() as PurchaseOrderStatus[],
      from: range.from,
      to: range.to,
      code: (search['code'] ?? '').trim(),
      product: (search['product'] ?? '').trim(),
      supplier: (search['supplier'] ?? '').trim(),
      note: (search['note'] ?? '').trim(),
      createdBy: this.createdBy(),
      completedBy: this.completedBy(),
      page,
      size,
    };
  }

  readonly pageState = toSignal(
    toObservable(
      computed(() => ({
        query: this.listQuery(this.page(), this.pageSize()),
        tick: this.purchaseOrderService.changed(),
      })),
    ).pipe(switchMap(({ query }) => toApiState<PurchaseOrderPage>(this.purchaseOrderService.list(query)))),
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

  // ---- selection: what the toolbar acts on ----

  readonly selectedIds = signal<number[]>([]);

  readonly selectedCount = computed(() => this.selectedIds().length);

  isRowSelected(id: number): boolean {
    return this.selectedIds().includes(id);
  }

  toggleRowSelection(id: number, event: Event): void {
    // The tick lives inside a row that expands on click; without this, ticking
    // a box would also open the detail panel under it.
    event.stopPropagation();
    this.selectedIds.update((ids) => (ids.includes(id) ? ids.filter((i) => i !== id) : [...ids, id]));
  }

  /** The rows on screen - "select all" means this page, which is what the box sits above. */
  private readonly pageIds = computed(() =>
    (this.pageState().data?.purchaseOrders ?? []).map((po) => po.id),
  );

  readonly allOnPageSelected = computed(() => {
    const ids = this.pageIds();
    return ids.length > 0 && ids.every((id) => this.selectedIds().includes(id));
  });

  toggleAllOnPage(): void {
    const ids = this.pageIds();
    if (this.allOnPageSelected()) {
      this.selectedIds.update((selected) => selected.filter((id) => !ids.includes(id)));
    } else {
      this.selectedIds.update((selected) => [...new Set([...selected, ...ids])]);
    }
  }

  clearSelection(): void {
    this.selectedIds.set([]);
  }

  private readonly selectedOrders = computed<PurchaseOrderDTO[]>(() =>
    (this.pageState().data?.purchaseOrders ?? []).filter((po) => this.selectedIds().includes(po.id)),
  );

  // ---- the star column ----

  /**
   * Stars already saved this session. Held apart from the fetched rows so a
   * star does not cost a refetch of the whole list; an entry only ever exists
   * once the server has accepted it, so it cannot disagree with what is stored.
   */
  private readonly starOverrides = signal<Record<number, boolean>>({});

  isStarred(po: PurchaseOrderDTO): boolean {
    return this.starOverrides()[po.id] ?? po.starred;
  }

  toggleStar(po: PurchaseOrderDTO, event: Event): void {
    event.stopPropagation();
    const next = !this.isStarred(po);
    this.purchaseOrderService.setStarred(po.id, next).subscribe({
      next: () => this.starOverrides.update((map) => ({ ...map, [po.id]: next })),
      error: (err) => this.actionError.set(toActionError(err)),
    });
  }

  // ---- toolbar actions ----

  readonly actionError = signal<ActionError | null>(null);
  readonly actionMessage = signal<string | null>(null);
  readonly busy = signal(false);
  readonly exporting = signal(false);

  dismissAction(): void {
    this.actionError.set(null);
    this.actionMessage.set(null);
  }

  /** Only a Phiếu tạm can be abandoned - PurchaseOrderService#cancel refuses anything else. */
  private readonly cancellableBlockedReason = computed(() => {
    const selected = this.selectedOrders();
    if (selected.length === 0) {
      return 'Chưa chọn phiếu nào';
    }
    return selected.every((po) => po.status === 'DRAFT')
      ? null
      : 'Chỉ hủy được phiếu ở trạng thái Phiếu tạm';
  });

  readonly bulkMenuItems = computed<ActionMenuItem[]>(() => {
    const blocked = this.cancellableBlockedReason();
    return [
      {
        key: 'cancel',
        label: 'Hủy phiếu nhập',
        danger: true,
        disabled: !!blocked,
        disabledReason: blocked ?? undefined,
      },
    ];
  });

  onBulkPicked(key: string): void {
    if (key === 'cancel') {
      this.bulkCancel();
    }
  }

  /**
   * No bulk endpoint behind this - each receipt is cancelled on its own and
   * the results are counted up, which is honest about what happened when one
   * of them is refused mid-way rather than reporting all-or-nothing.
   */
  private bulkCancel(): void {
    const ids = this.selectedOrders()
      .filter((po) => po.status === 'DRAFT')
      .map((po) => po.id);
    if (ids.length === 0 || this.busy()) {
      return;
    }
    if (!window.confirm(`Hủy ${ids.length} phiếu nhập đã chọn?`)) {
      return;
    }
    this.busy.set(true);
    this.dismissAction();
    forkJoin(
      ids.map((id) =>
        this.purchaseOrderService.cancel(id).pipe(
          map(() => true),
          catchError(() => of(false)),
        ),
      ),
    ).subscribe((results) => {
      const cancelled = results.filter(Boolean).length;
      const failed = results.length - cancelled;
      this.actionMessage.set(
        failed === 0
          ? `Đã hủy ${cancelled} phiếu nhập.`
          : `Đã hủy ${cancelled} phiếu nhập, ${failed} phiếu không hủy được.`,
      );
      this.busy.set(false);
      this.purchaseOrderService.notifyChanged();
      // The rows that moved may no longer match the filters, so a selection
      // kept here would act on ids that are not on screen any more.
      this.clearSelection();
    });
  }

  /**
   * "Xuất file" - the ticked rows if there are any, otherwise every receipt
   * the filters match rather than just the page on screen, in the columns the
   * shop chose to see. Money goes out as plain numbers so Excel can sum the
   * column; the currency symbol would make it text.
   */
  exportCsv(): void {
    if (this.exporting()) {
      return;
    }
    const selected = this.selectedOrders();
    if (selected.length > 0) {
      this.writeCsv(selected);
      return;
    }
    const result = this.pageState().data;
    if (!result || result.totalItems === 0) {
      return;
    }
    this.exporting.set(true);
    this.purchaseOrderService.list(this.listQuery(0, result.totalItems)).subscribe({
      next: (page) => {
        this.writeCsv(page.purchaseOrders);
        this.exporting.set(false);
      },
      error: (err) => {
        this.exporting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  private writeCsv(rows: PurchaseOrderDTO[]): void {
    const keys = this.visibleColumns();
    const headers = keys.map((key) => COLUMNS.find((c) => c.key === key)?.label ?? key);
    exportRowsToCsv(headers, rows.map((po) => keys.map((key) => this.cellValue(po, key))), 'nhap-hang.csv');
  }

  private cellValue(po: PurchaseOrderDTO, key: string): string {
    switch (key) {
      case 'code':
        return po.code;
      case 'createdAt':
        return new Date(po.createdAt).toLocaleString('vi-VN');
      case 'supplierCode':
        return po.supplierCode ?? '';
      case 'supplierName':
        return po.supplierName ?? '';
      case 'totalGoodsValue':
        return String(po.totalGoodsValue);
      case 'discount':
        return String(po.discountAmount);
      case 'supplierCharge':
        return String(po.supplierChargeAmount);
      case 'payable':
        return String(po.payableAmount);
      case 'paid':
        return String(po.amountPaid);
      case 'debt':
        return String(po.debtAmount);
      case 'otherCosts':
        return String(po.otherCosts);
      case 'status':
        return this.statusLabel(po.status);
      case 'creator':
        return po.createdByUsername ?? '';
      case 'receiver':
        return po.completedByUsername ?? '';
      case 'note':
        return po.note ?? '';
      default:
        return '';
    }
  }

  statusLabel(status: PurchaseOrderStatus): string {
    return STATUS_LABELS[status];
  }
}
