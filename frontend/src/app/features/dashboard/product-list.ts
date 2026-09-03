import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterOutlet } from '@angular/router';
import { switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { toApiState } from './api-state.util';
import { ActionErrorBanner } from './action-error-banner';
import { exportProductsToCsv } from './product-csv-export.util';
import { ProductAdminSortBy, ProductDTO, ProductPage, SortDirection } from './product-admin.models';
import { ProductAdminService } from './product-admin.service';
import { ProductCategoryService } from './product-category.service';
import { ActionError, toActionError } from './subscription-error.util';

type ActiveFilter = 'all' | 'active' | 'inactive';
type StockFilter = 'all' | 'in' | 'out';

interface ProductGroupRow {
  key: string;
  isGroup: boolean;
  members: ProductDTO[];
}

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [RouterLink, RouterOutlet, NgTemplateOutlet, VndCurrencyPipe, DatePipe, ActionErrorBanner],
  templateUrl: './product-list.html',
})
export class ProductList {
  private readonly productService = inject(ProductAdminService);
  private readonly categoryService = inject(ProductCategoryService);
  private readonly authService = inject(AuthService);

  readonly currentUser = this.authService.currentUser;
  readonly isOwner = computed(() => this.currentUser()?.storeRole === 'OWNER');

  readonly categories = toSignal(
    toApiState(this.categoryService.list()),
    { initialValue: { data: null, error: null } },
  );

  readonly page = signal(0);
  readonly pageSize = signal(15);
  readonly searchQuery = signal('');
  readonly activeFilter = signal<ActiveFilter>('all');
  /** "Nhóm hàng" sidebar filter - null = tất cả. Disabled while searching, same as activeFilter/stockFilter (the admin search query doesn't support these). */
  readonly categoryFilter = signal<number | null>(null);
  /** "Tồn kho" sidebar filter, matching KiotViet's stock-status filter. */
  readonly stockFilter = signal<StockFilter>('all');
  readonly sortBy = signal<ProductAdminSortBy>('createdAt');
  readonly sortDirection = signal<SortDirection>('DESC');

  readonly isSearching = computed(() => this.searchQuery().trim().length > 0);

  readonly pageState = toSignal(
    toObservable(
      computed(() => ({
        query: this.searchQuery().trim(),
        active: this.activeFilter(),
        categoryId: this.categoryFilter(),
        stock: this.stockFilter(),
        page: this.page(),
        size: this.pageSize(),
        sortBy: this.sortBy(),
        sortDirection: this.sortDirection(),
        tick: this.productService.changed(),
      })),
    ).pipe(
      switchMap(({ query, active, categoryId, stock, page, size, sortBy, sortDirection }) => {
        if (query) {
          return toApiState<ProductPage>(this.productService.search(query, page, size, sortBy, sortDirection));
        }
        const activeParam = active === 'all' ? undefined : active === 'active';
        const inStockParam = stock === 'all' ? undefined : stock === 'in';
        return toApiState<ProductPage>(
          this.productService.list(page, size, sortBy, sortDirection, activeParam, categoryId ?? undefined, inStockParam),
        );
      }),
    ),
    { initialValue: { data: null, error: null } },
  );

  readonly rangeText = computed(() => {
    const result = this.pageState().data;
    if (!result || result.totalItems === 0) {
      return null;
    }
    const from = result.currentPage * this.pageSize() + 1;
    const to = Math.min(from + result.products.length - 1, result.totalItems);
    return `${from} - ${to} trong ${result.totalItems} hàng hóa`;
  });

  readonly confirmingDeleteId = signal<number | null>(null);
  readonly actionError = signal<ActionError | null>(null);

  readonly createMenuOpen = signal(false);
  readonly exporting = signal(false);

  toggleCreateMenu(): void {
    this.createMenuOpen.update((open) => !open);
  }

  closeCreateMenu(): void {
    this.createMenuOpen.set(false);
  }

  /** "Xuất file" - exports whatever the current search/status/sidebar filters match (up to 1000 rows), not just the current page. No dedicated export endpoint on the backend; reuses the same list/search calls the table already makes. */
  exportCsv(): void {
    this.exporting.set(true);
    const activeParam = this.activeFilter() === 'all' ? undefined : this.activeFilter() === 'active';
    const inStockParam = this.stockFilter() === 'all' ? undefined : this.stockFilter() === 'in';
    const query = this.searchQuery().trim();
    const source$ = query
      ? this.productService.search(query, 0, 1000, this.sortBy(), this.sortDirection())
      : this.productService.list(
          0,
          1000,
          this.sortBy(),
          this.sortDirection(),
          activeParam,
          this.categoryFilter() ?? undefined,
          inStockParam,
        );
    source$.subscribe({
      next: (result) => {
        this.exporting.set(false);
        const selected = this.selectedIds();
        const products = selected.size > 0 ? result.products.filter((p) => selected.has(p.id)) : result.products;
        exportProductsToCsv(products);
      },
      error: (err: HttpErrorResponse) => {
        this.exporting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  /**
   * Groups Color x Size variant siblings (same variantGroupId) into one
   * collapsible row. Page-scoped only - a sibling that lands on a different
   * page (different sort/search) just renders as an ordinary ungrouped row.
   * A variantGroupId shared by only one row (e.g. a product created through
   * the units/attributes popup with a single combination) is *not* treated
   * as a group - it renders as a plain row, same as any other single
   * product, so it keeps its own photo, SKU and creation time instead of
   * the group header's placeholders.
   */
  readonly expandedGroups = signal<Set<string>>(new Set());

  readonly groupedRows = computed<ProductGroupRow[]>(() => {
    const products = this.pageState().data?.products ?? [];
    const order: string[] = [];
    const groups = new Map<string, ProductGroupRow>();
    for (const p of products) {
      const key = p.variantGroupId ?? `single-${p.id}`;
      let group = groups.get(key);
      if (!group) {
        group = { key, isGroup: p.variantGroupId !== null, members: [] };
        groups.set(key, group);
        order.push(key);
      }
      group.members.push(p);
    }
    return order.map((key) => {
      const group = groups.get(key)!;
      return group.members.length > 1 ? group : { ...group, isGroup: false };
    });
  });

  /**
   * "Tích chọn" row selection, matching KiotViet's checkbox column - persists
   * across pagination (selecting on page 1, then paging to page 2, keeps
   * page 1's checks) but only ids still present on the current page drive
   * the header's select-all/indeterminate state.
   */
  readonly selectedIds = signal<Set<number>>(new Set());

  readonly currentPageProductIds = computed(() => (this.pageState().data?.products ?? []).map((p) => p.id));

  readonly allOnPageSelected = computed(() => {
    const ids = this.currentPageProductIds();
    return ids.length > 0 && ids.every((id) => this.selectedIds().has(id));
  });

  readonly someOnPageSelected = computed(() => {
    const ids = this.currentPageProductIds();
    const selected = this.selectedIds();
    return !this.allOnPageSelected() && ids.some((id) => selected.has(id));
  });

  readonly selectedCount = computed(() => this.selectedIds().size);

  toggleSelectAllOnPage(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const ids = this.currentPageProductIds();
    this.selectedIds.update((current) => {
      const next = new Set(current);
      ids.forEach((id) => (checked ? next.add(id) : next.delete(id)));
      return next;
    });
  }

  isSelected(id: number): boolean {
    return this.selectedIds().has(id);
  }

  toggleSelect(id: number, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.selectedIds.update((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  isGroupSelected(members: ProductDTO[]): boolean {
    const selected = this.selectedIds();
    return members.every((m) => selected.has(m.id));
  }

  isGroupIndeterminate(members: ProductDTO[]): boolean {
    const selected = this.selectedIds();
    return !this.isGroupSelected(members) && members.some((m) => selected.has(m.id));
  }

  toggleGroupSelect(members: ProductDTO[], event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.selectedIds.update((current) => {
      const next = new Set(current);
      members.forEach((m) => (checked ? next.add(m.id) : next.delete(m.id)));
      return next;
    });
  }

  clearSelection(): void {
    this.selectedIds.set(new Set());
  }

  toggleGroup(key: string): void {
    this.expandedGroups.update((expanded) => {
      const next = new Set(expanded);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  isGroupExpanded(key: string): boolean {
    return this.expandedGroups().has(key);
  }

  priceRange(members: ProductDTO[]): { min: number; max: number } {
    const prices = members.map((m) => m.price);
    return { min: Math.min(...prices), max: Math.max(...prices) };
  }

  costRange(members: ProductDTO[]): { min: number; max: number } | null {
    const costs = members.map((m) => m.costPrice).filter((c): c is number => c != null);
    if (costs.length === 0) {
      return null;
    }
    return { min: Math.min(...costs), max: Math.max(...costs) };
  }

  stockSum(members: ProductDTO[]): number {
    return members.reduce((sum, m) => sum + m.stockQuantity, 0);
  }

  /** "Khách đặt" total across a variant group's members. */
  pendingQuantitySum(members: ProductDTO[]): number {
    return members.reduce((sum, m) => sum + m.pendingCustomerQuantity, 0);
  }

  /** Sum of Tồn kho across the currently loaded page, shown in the totals row like KiotViet's list header. */
  readonly pageStockTotal = computed(() => (this.pageState().data?.products ?? []).reduce((sum, p) => sum + p.stockQuantity, 0));

  /** Sum of Khách đặt across the currently loaded page, shown in the totals row alongside Tồn kho. */
  readonly pagePendingQuantityTotal = computed(() =>
    (this.pageState().data?.products ?? []).reduce((sum, p) => sum + p.pendingCustomerQuantity, 0),
  );

  /** Variant siblings are named "{base} - {color} - {size}" - show just the base in the group summary row. */
  groupDisplayName(members: ProductDTO[]): string {
    return members[0].name.split(' - ')[0];
  }

  onSearchInput(event: Event): void {
    this.searchQuery.set((event.target as HTMLInputElement).value);
    this.page.set(0);
  }

  onActiveFilterChange(event: Event): void {
    this.activeFilter.set((event.target as HTMLSelectElement).value as ActiveFilter);
    this.page.set(0);
  }

  onCategoryFilterChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.categoryFilter.set(value ? Number(value) : null);
    this.page.set(0);
  }

  onStockFilterChange(event: Event): void {
    this.stockFilter.set((event.target as HTMLSelectElement).value as StockFilter);
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

  private refresh(): void {
    this.productService.notifyChanged();
  }

  toggleStatus(product: ProductDTO): void {
    this.actionError.set(null);
    this.productService.updateStatus(product.id, !product.active).subscribe({
      next: () => this.refresh(),
      error: (err: HttpErrorResponse) => this.actionError.set(toActionError(err)),
    });
  }

  requestDelete(productId: number): void {
    this.confirmingDeleteId.set(productId);
  }

  cancelDelete(): void {
    this.confirmingDeleteId.set(null);
  }

  confirmDelete(productId: number): void {
    this.actionError.set(null);
    this.productService.delete(productId).subscribe({
      next: () => {
        this.confirmingDeleteId.set(null);
        this.refresh();
      },
      error: (err: HttpErrorResponse) => {
        this.confirmingDeleteId.set(null);
        this.actionError.set(toActionError(err));
      },
    });
  }

  primaryImage(product: ProductDTO): string | null {
    return (product.images.find((i) => i.isPrimary) ?? product.images[0])?.imageUrl ?? null;
  }

  categoryNames(product: ProductDTO): string {
    return product.categories.length > 0 ? product.categories.map((c) => c.name).join(', ') : '—';
  }
}
