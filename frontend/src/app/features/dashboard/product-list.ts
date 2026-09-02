import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterOutlet } from '@angular/router';
import { switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { toApiState } from './api-state.util';
import { ActionErrorBanner } from './action-error-banner';
import { ProductAdminSortBy, ProductDTO, ProductPage, SortDirection } from './product-admin.models';
import { ProductAdminService } from './product-admin.service';
import { StatCard } from './stat-card';
import { ActionError, toActionError } from './subscription-error.util';

type ActiveFilter = 'all' | 'active' | 'inactive';

interface ProductGroupRow {
  key: string;
  isGroup: boolean;
  members: ProductDTO[];
}

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [RouterLink, RouterOutlet, NgTemplateOutlet, VndCurrencyPipe, StatCard, ActionErrorBanner],
  templateUrl: './product-list.html',
})
export class ProductList {
  private readonly productService = inject(ProductAdminService);
  private readonly authService = inject(AuthService);

  readonly currentUser = this.authService.currentUser;
  readonly isOwner = computed(() => this.currentUser()?.storeRole === 'OWNER');

  readonly statsState = toSignal(toApiState(this.productService.getStats()), {
    initialValue: { data: null, error: null },
  });

  readonly page = signal(0);
  readonly searchQuery = signal('');
  readonly activeFilter = signal<ActiveFilter>('all');
  readonly sortBy = signal<ProductAdminSortBy>('createdAt');
  readonly sortDirection = signal<SortDirection>('DESC');

  readonly isSearching = computed(() => this.searchQuery().trim().length > 0);

  readonly pageState = toSignal(
    toObservable(
      computed(() => ({
        query: this.searchQuery().trim(),
        active: this.activeFilter(),
        page: this.page(),
        sortBy: this.sortBy(),
        sortDirection: this.sortDirection(),
        tick: this.productService.changed(),
      })),
    ).pipe(
      switchMap(({ query, active, page, sortBy, sortDirection }) => {
        if (query) {
          return toApiState<ProductPage>(this.productService.search(query, page, 20, sortBy, sortDirection));
        }
        const activeParam = active === 'all' ? undefined : active === 'active';
        return toApiState<ProductPage>(
          this.productService.list(page, 20, sortBy, sortDirection, activeParam),
        );
      }),
    ),
    { initialValue: { data: null, error: null } },
  );

  readonly confirmingDeleteId = signal<number | null>(null);
  readonly actionError = signal<ActionError | null>(null);

  /**
   * Groups Color x Size variant siblings (same variantGroupId) into one
   * collapsible row. Page-scoped only - a sibling that lands on a different
   * page (different sort/search) just renders as an ordinary ungrouped row.
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
    return order.map((key) => groups.get(key)!);
  });

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

  stockSum(members: ProductDTO[]): number {
    return members.reduce((sum, m) => sum + m.stockQuantity, 0);
  }

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
