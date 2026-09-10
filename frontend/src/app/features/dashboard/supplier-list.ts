import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { ActionMenu, ActionMenuItem } from './action-menu';
import { toApiState } from './api-state.util';
import { ColumnDef, ColumnPicker } from './column-picker';
import { loadColumnPrefs, saveColumnPrefs } from './column-prefs.util';
import { exportRowsToCsv } from './csv-export.util';
import { FilterSelect, SelectOption } from './filter-select';
import { SearchField, SearchPanel, SearchValues } from './search-panel';
import { ActionError, toActionError } from './subscription-error.util';
import { SupplierDetailPanel } from './supplier-detail-panel';
import { SupplierFormModal } from './supplier-form-modal';
import { SupplierDTO, SupplierListQuery, SupplierPage, SupplierStatusFilter } from './supplier.models';
import { SupplierService } from './supplier.service';
import { TIME_PRESETS, TimeMode, TimePreset, formatIsoDate, presetRange } from './time-filter.util';

import { DatePipe } from '@angular/common';
import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';

/**
 * The columns KiotViet's Nhà cung cấp table can show. Everything past the
 * sixth is this app's own supplier record rather than an invention: the
 * modal that creates a supplier fills all of them.
 */
const COLUMNS: ColumnDef[] = [
  { key: 'code', label: 'Mã nhà cung cấp' },
  { key: 'name', label: 'Tên nhà cung cấp' },
  { key: 'phone', label: 'Điện thoại' },
  { key: 'email', label: 'Email' },
  { key: 'debt', label: 'Nợ cần trả hiện tại' },
  { key: 'totalPurchase', label: 'Tổng mua' },
  { key: 'groupName', label: 'Nhóm nhà cung cấp' },
  { key: 'address', label: 'Địa chỉ' },
  { key: 'region', label: 'Khu vực' },
  { key: 'ward', label: 'Phường/Xã' },
  { key: 'companyName', label: 'Tên công ty' },
  { key: 'taxCode', label: 'Mã số thuế' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'createdAt', label: 'Ngày tạo' },
  { key: 'note', label: 'Ghi chú' },
];

/** What KiotViet's own Nhà cung cấp list shows before anyone touches the column picker. */
const DEFAULT_COLUMNS = ['code', 'name', 'phone', 'email', 'debt', 'totalPurchase'];

const COLUMN_STORAGE_KEY = 'tryum.supplier-list.columns';

/**
 * "Nhà cung cấp" - the other half of the Mua hàng tab, on the same list-screen
 * kit as Nhập hàng and Hóa đơn: a white filter card down the left, a search
 * box with a panel behind its sliders icon, a column chooser that remembers
 * itself, CSV export, and a detail panel that opens inside the row.
 *
 * The two money columns ("Tổng mua", "Nợ cần trả hiện tại") are rolled up
 * from goods receipts by the API - nothing about them is stored on the
 * supplier itself.
 */
@Component({
  selector: 'app-supplier-list',
  standalone: true,
  imports: [
    DatePipe,
    VndCurrencyPipe,
    ActionMenu,
    ColumnPicker,
    FilterSelect,
    SearchPanel,
    SupplierDetailPanel,
    SupplierFormModal,
  ],
  templateUrl: './supplier-list.html',
})
export class SupplierList {
  private readonly supplierService = inject(SupplierService);

  /** Clicking a row expands its detail inline, the way KiotViet's own list does. */
  readonly selectedId = signal<number | null>(null);

  toggleSelect(id: number): void {
    this.selectedId.update((current) => (current === id ? null : id));
  }

  // ---- sidebar filters ----

  /**
   * "Nhóm nhà cung cấp". KiotViet's own box carries a "Tạo mới" link beside
   * it; there is nothing to create here, because a group is free text typed
   * on the supplier itself (same as Product's "Thương hiệu") - so the options
   * are the groups the shop has actually used.
   */
  private readonly groupNames = toSignal(
    toObservable(computed(() => this.supplierService.changed())).pipe(
      switchMap(() => this.supplierService.groups().pipe(catchError(() => of({ groups: [] as string[] })))),
    ),
    { initialValue: { groups: [] as string[] } },
  );

  readonly groupOptions = computed<SelectOption[]>(() => [
    { value: '', label: 'Tất cả các nhóm' },
    ...this.groupNames().groups.map((name) => ({ value: name, label: name })),
  ]);

  readonly group = signal('');

  setGroup(value: string): void {
    this.group.set(value);
    this.page.set(0);
  }

  /** "Tổng mua" and "Nợ hiện tại" - an empty box means unbounded, not zero. */
  readonly totalFrom = signal<number | null>(null);
  readonly totalTo = signal<number | null>(null);
  readonly debtFrom = signal<number | null>(null);
  readonly debtTo = signal<number | null>(null);

  onRangeInput(target: 'totalFrom' | 'totalTo' | 'debtFrom' | 'debtTo', event: Event): void {
    const raw = (event.target as HTMLInputElement).value.trim();
    const value = raw === '' ? null : Number(raw);
    this[target].set(value === null || Number.isNaN(value) ? null : value);
    this.page.set(0);
  }

  readonly timePresetOptions: SelectOption[] = TIME_PRESETS.map((preset) => ({
    value: preset.value,
    label: preset.label,
  }));

  readonly timeMode = signal<TimeMode>('preset');
  /** KiotViet opens this screen on "Toàn thời gian" - a supplier's Tổng mua is a running total, not a monthly one. */
  readonly timePreset = signal<TimePreset>('all');
  readonly customFrom = signal<string>('');
  readonly customTo = signal<string>('');
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

  /** KiotViet's three pills, opening on "Đang hoạt động" the way theirs does. */
  readonly status = signal<SupplierStatusFilter>('active');

  setStatus(value: SupplierStatusFilter): void {
    this.status.set(value);
    this.page.set(0);
    this.selectedId.set(null);
  }

  // ---- the search box and the panel behind its sliders icon ----

  readonly searchFields: SearchField[] = [
    { key: 'query', placeholder: 'Theo mã, tên nhà cung cấp' },
    { key: 'phone', placeholder: 'Theo số điện thoại' },
    { key: 'note', placeholder: 'Theo ghi chú' },
  ];

  readonly search = signal<SearchValues>({ query: '', phone: '', note: '' });

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

  /** How many columns an expanded detail row has to span - the tick column is always drawn. */
  readonly columnCount = computed(() => this.visibleColumns().length + 1);

  // ---- paging ----

  readonly page = signal(0);
  readonly pageSize = signal(15);

  private listQuery(page: number, size: number): SupplierListQuery {
    const range = this.dateRange();
    const search = this.search();
    return {
      query: search['query'] ?? '',
      phone: search['phone'] ?? '',
      note: search['note'] ?? '',
      group: this.group(),
      status: this.status(),
      totalFrom: this.totalFrom(),
      totalTo: this.totalTo(),
      debtFrom: this.debtFrom(),
      debtTo: this.debtTo(),
      from: range.from,
      to: range.to,
      page,
      size,
    };
  }

  readonly pageState = toSignal(
    toObservable(
      computed(() => ({
        query: this.listQuery(this.page(), this.pageSize()),
        tick: this.supplierService.changed(),
      })),
    ).pipe(switchMap(({ query }) => toApiState<SupplierPage>(this.supplierService.list(query)))),
    { initialValue: { data: null, error: null } },
  );

  readonly rangeText = computed(() => {
    const result = this.pageState().data;
    if (!result || result.totalItems === 0) {
      return null;
    }
    const from = result.currentPage * this.pageSize() + 1;
    const to = Math.min(from + result.suppliers.length - 1, result.totalItems);
    return `${from} - ${to} trong ${result.totalItems} nhà cung cấp`;
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

  private readonly pageIds = computed(() => (this.pageState().data?.suppliers ?? []).map((s) => s.id));

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

  private readonly selectedSuppliers = computed<SupplierDTO[]>(() =>
    (this.pageState().data?.suppliers ?? []).filter((s) => this.selectedIds().includes(s.id)),
  );

  // ---- the create/edit modal ----

  readonly formOpen = signal(false);
  readonly editingSupplier = signal<SupplierDTO | null>(null);

  openCreateForm(): void {
    this.editingSupplier.set(null);
    this.formOpen.set(true);
  }

  openEditForm(supplier: SupplierDTO): void {
    this.editingSupplier.set(supplier);
    this.formOpen.set(true);
  }

  closeForm(): void {
    this.formOpen.set(false);
  }

  onSaved(): void {
    this.formOpen.set(false);
    this.supplierService.notifyChanged();
  }

  /** A deleted or stopped supplier may no longer match the filters, so the open panel closes with it. */
  onDetailChanged(): void {
    this.selectedId.set(null);
    this.clearSelection();
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

  readonly bulkMenuItems = computed<ActionMenuItem[]>(() => {
    const selected = this.selectedSuppliers();
    const activeCount = selected.filter((s) => s.active).length;
    return [
      {
        key: 'deactivate',
        label: 'Ngừng hoạt động',
        disabled: activeCount === 0,
        disabledReason: 'Các nhà cung cấp đã chọn đều đang ngừng hoạt động',
      },
      {
        key: 'activate',
        label: 'Kích hoạt lại',
        disabled: activeCount === selected.length,
        disabledReason: 'Các nhà cung cấp đã chọn đều đang hoạt động',
      },
      {
        key: 'delete',
        label: 'Xóa',
        danger: true,
        separatorBefore: true,
        disabled: selected.length === 0,
        disabledReason: 'Chưa chọn nhà cung cấp nào',
      },
    ];
  });

  onBulkPicked(key: string): void {
    if (key === 'delete') {
      this.bulkDelete();
    } else {
      this.bulkSetActive(key === 'activate');
    }
  }

  /**
   * No bulk endpoint behind these - each supplier is changed on its own and
   * the results counted up, which stays honest when one of them is refused
   * mid-way rather than reporting all-or-nothing (same as Nhập hàng's own
   * bulk cancel).
   */
  private bulkSetActive(active: boolean): void {
    const targets = this.selectedSuppliers().filter((s) => s.active !== active);
    if (targets.length === 0 || this.busy()) {
      return;
    }
    const verb = active ? 'Kích hoạt lại' : 'Ngừng hoạt động';
    if (!window.confirm(`${verb} ${targets.length} nhà cung cấp đã chọn?`)) {
      return;
    }
    this.busy.set(true);
    this.dismissAction();
    forkJoin(
      targets.map((s) =>
        this.supplierService.setActive(s.id, active).pipe(
          map(() => true),
          catchError(() => of(false)),
        ),
      ),
    ).subscribe((results) => {
      const done = results.filter(Boolean).length;
      const failed = results.length - done;
      this.actionMessage.set(
        failed === 0
          ? `Đã ${verb.toLowerCase()} ${done} nhà cung cấp.`
          : `Đã ${verb.toLowerCase()} ${done} nhà cung cấp, ${failed} nhà cung cấp không đổi được.`,
      );
      this.finishBulk();
    });
  }

  /**
   * A supplier that already appears on a goods receipt is refused by the API
   * (409) rather than taking the receipt's record of where the goods came
   * from with it - so a mixed selection is reported as what went and what
   * stayed, with the stopped-instead advice the panel gives.
   */
  private bulkDelete(): void {
    const targets = this.selectedSuppliers();
    if (targets.length === 0 || this.busy()) {
      return;
    }
    if (!window.confirm(`Xóa ${targets.length} nhà cung cấp đã chọn? Thao tác này không hoàn tác được.`)) {
      return;
    }
    this.busy.set(true);
    this.dismissAction();
    forkJoin(
      targets.map((s) =>
        this.supplierService.delete(s.id).pipe(
          map(() => true),
          catchError(() => of(false)),
        ),
      ),
    ).subscribe((results) => {
      const deleted = results.filter(Boolean).length;
      const failed = results.length - deleted;
      this.actionMessage.set(
        failed === 0
          ? `Đã xóa ${deleted} nhà cung cấp.`
          : `Đã xóa ${deleted} nhà cung cấp, ${failed} nhà cung cấp đã có phiếu nhập nên chỉ có thể ngừng hoạt động.`,
      );
      this.finishBulk();
    });
  }

  private finishBulk(): void {
    this.busy.set(false);
    this.supplierService.notifyChanged();
    // The rows that moved may no longer match the filters, so a selection
    // kept here would act on ids that are not on screen any more.
    this.clearSelection();
    this.selectedId.set(null);
  }

  /**
   * "Xuất file" - the ticked rows if there are any, otherwise every supplier
   * the filters match rather than just the page on screen, in the columns the
   * shop chose to see. Money goes out as plain numbers so Excel can sum the
   * column.
   */
  exportCsv(): void {
    if (this.exporting()) {
      return;
    }
    const selected = this.selectedSuppliers();
    if (selected.length > 0) {
      this.writeCsv(selected);
      return;
    }
    const result = this.pageState().data;
    if (!result || result.totalItems === 0) {
      return;
    }
    this.exporting.set(true);
    this.supplierService.list(this.listQuery(0, result.totalItems)).subscribe({
      next: (page) => {
        this.writeCsv(page.suppliers);
        this.exporting.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.exporting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  private writeCsv(rows: SupplierDTO[]): void {
    const keys = this.visibleColumns();
    const headers = keys.map((key) => COLUMNS.find((c) => c.key === key)?.label ?? key);
    exportRowsToCsv(headers, rows.map((s) => keys.map((key) => this.cellValue(s, key))), 'nha-cung-cap.csv');
  }

  private cellValue(supplier: SupplierDTO, key: string): string {
    switch (key) {
      case 'code':
        return supplier.code;
      case 'name':
        return supplier.name;
      case 'phone':
        return supplier.phone ?? '';
      case 'email':
        return supplier.email ?? '';
      case 'debt':
        return String(supplier.currentDebt);
      case 'totalPurchase':
        return String(supplier.totalPurchase);
      case 'groupName':
        return supplier.groupName ?? '';
      case 'address':
        return supplier.address ?? '';
      case 'region':
        return supplier.region ?? '';
      case 'ward':
        return supplier.ward ?? '';
      case 'companyName':
        return supplier.companyName ?? '';
      case 'taxCode':
        return supplier.taxCode ?? '';
      case 'status':
        return supplier.active ? 'Đang hoạt động' : 'Ngừng hoạt động';
      case 'createdAt':
        return new Date(supplier.createdAt).toLocaleDateString('vi-VN');
      case 'note':
        return supplier.note ?? '';
      default:
        return '';
    }
  }
}
