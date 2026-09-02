import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
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

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [RouterLink, VndCurrencyPipe, StatCard, ActionErrorBanner],
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
  private readonly refreshTick = signal(0);

  readonly isSearching = computed(() => this.searchQuery().trim().length > 0);

  readonly pageState = toSignal(
    toObservable(
      computed(() => ({
        query: this.searchQuery().trim(),
        active: this.activeFilter(),
        page: this.page(),
        sortBy: this.sortBy(),
        sortDirection: this.sortDirection(),
        tick: this.refreshTick(),
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
    this.refreshTick.update((t) => t + 1);
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
