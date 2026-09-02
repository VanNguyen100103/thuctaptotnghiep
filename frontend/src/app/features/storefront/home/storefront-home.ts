import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';

import { CartService } from '../../../core/cart/cart.service';
import { extractErrorMessage } from '../../../core/http/api-error';
import { VndCurrencyPipe } from '../../../core/currency/vnd-currency.pipe';
import { StorefrontCatalogService } from '../storefront-catalog.service';
import { ProductPage, ProductSortBy, SortDirection, Store } from '../storefront.models';

interface StoreState {
  store: Store | null;
  error: string | null;
}

interface ProductPageState {
  data: ProductPage | null;
  error: string | null;
}

@Component({
  selector: 'app-storefront-home',
  standalone: true,
  imports: [RouterLink, VndCurrencyPipe],
  templateUrl: './storefront-home.html',
})
export class StorefrontHome {
  private readonly route = inject(ActivatedRoute);
  private readonly catalogService = inject(StorefrontCatalogService);
  private readonly cartService = inject(CartService);

  private readonly paramMap = toSignal(this.route.paramMap, { requireSync: true });
  readonly storeSlug = computed(() => this.paramMap()!.get('storeSlug')!);

  readonly page = signal(0);
  readonly sortBy = signal<ProductSortBy>('createdAt');
  readonly sortDirection = signal<SortDirection>('DESC');

  readonly storeState = toSignal(
    toObservable(this.storeSlug).pipe(
      switchMap((slug) =>
        this.catalogService.getStore(slug).pipe(
          switchMap((store) => of<StoreState>({ store, error: null })),
          catchError((err: HttpErrorResponse) => of<StoreState>({ store: null, error: extractErrorMessage(err) })),
        ),
      ),
    ),
    { initialValue: { store: null, error: null } as StoreState },
  );

  readonly categories = toSignal(toObservable(this.storeSlug).pipe(switchMap((slug) => this.catalogService.getCategories(slug))), {
    initialValue: [],
  });

  readonly productPageState = toSignal(
    toObservable(
      computed(() => ({
        slug: this.storeSlug(),
        page: this.page(),
        sortBy: this.sortBy(),
        sortDirection: this.sortDirection(),
      })),
    ).pipe(
      switchMap(({ slug, page, sortBy, sortDirection }) =>
        this.catalogService.getProducts(slug, page, 20, sortBy, sortDirection).pipe(
          switchMap((data) => of<ProductPageState>({ data, error: null })),
          catchError((err: HttpErrorResponse) => of<ProductPageState>({ data: null, error: extractErrorMessage(err) })),
        ),
      ),
    ),
    { initialValue: { data: null, error: null } as ProductPageState },
  );

  constructor() {
    effect(() => this.cartService.enterStore(this.storeSlug()));
  }

  changeSort(sortBy: ProductSortBy, sortDirection: SortDirection): void {
    this.sortBy.set(sortBy);
    this.sortDirection.set(sortDirection);
    this.page.set(0);
  }

  onSortChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    const sortBy: ProductSortBy = value === 'price' || value === 'soldCount' ? value : 'createdAt';
    this.changeSort(sortBy, 'DESC');
  }

  nextPage(): void {
    const totalPages = this.productPageState().data?.totalPages ?? 1;
    if (this.page() < totalPages - 1) {
      this.page.update((p) => p + 1);
    }
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
    }
  }

  primaryImage(images: { imageUrl: string; isPrimary: boolean }[]): string | null {
    return (images.find((i) => i.isPrimary) ?? images[0])?.imageUrl ?? null;
  }
}
