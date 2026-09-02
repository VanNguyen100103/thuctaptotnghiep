import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { CartService } from '../../../core/cart/cart.service';
import { extractErrorMessage } from '../../../core/http/api-error';
import { StorefrontCatalogService } from '../storefront-catalog.service';
import { Product, ProductVariantSummary } from '../storefront.models';
import { VndCurrencyPipe } from '../../../core/currency/vnd-currency.pipe';

interface ProductState {
  product: Product | null;
  variants: ProductVariantSummary[];
  error: string | null;
}

@Component({
  selector: 'app-storefront-product-detail',
  standalone: true,
  imports: [RouterLink, VndCurrencyPipe, DecimalPipe],
  templateUrl: './storefront-product-detail.html',
})
export class StorefrontProductDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly catalogService = inject(StorefrontCatalogService);
  private readonly cartService = inject(CartService);
  private readonly authService = inject(AuthService);

  private readonly paramMap = toSignal(this.route.paramMap, { requireSync: true });
  readonly storeSlug = computed(() => this.paramMap()!.get('storeSlug')!);
  readonly productId = computed(() => Number(this.paramMap()!.get('productId')));

  readonly productState = toSignal(
    toObservable(computed(() => ({ slug: this.storeSlug(), id: this.productId() }))).pipe(
      switchMap(({ slug, id }) =>
        this.catalogService.getProduct(slug, id).pipe(
          switchMap(({ product, variants }) => of<ProductState>({ product, variants, error: null })),
          catchError((err: HttpErrorResponse) =>
            of<ProductState>({ product: null, variants: [], error: extractErrorMessage(err) }),
          ),
        ),
      ),
    ),
    { initialValue: { product: null, variants: [], error: null } as ProductState },
  );

  /** More than 1 sibling means this product has real Color x Size variants (see backend variantGroupId). */
  readonly hasVariants = computed(() => this.productState().variants.length > 1);
  readonly variantColors = computed(() =>
    Array.from(new Set(this.productState().variants.map((v) => v.color).filter((c): c is string => !!c))),
  );
  readonly variantSizesForSelectedColor = computed(() => {
    const color = this.selectedColor();
    return Array.from(
      new Set(
        this.productState()
          .variants.filter((v) => !color || v.color === color)
          .map((v) => v.size)
          .filter((s): s is string => !!s),
      ),
    );
  });

  readonly selectedImageUrl = signal<string | null>(null);
  readonly selectedSize = signal<string | null>(null);
  readonly selectedColor = signal<string | null>(null);
  readonly quantity = signal(1);
  readonly adding = signal(false);
  readonly addError = signal<string | null>(null);
  readonly addSuccess = signal(false);

  readonly canAddToCart = computed(() => {
    const product = this.productState().product;
    if (!product || product.stockQuantity === 0) {
      return false;
    }
    if (product.availableSizes.length > 0 && !this.selectedSize()) {
      return false;
    }
    if (product.availableColors.length > 0 && !this.selectedColor()) {
      return false;
    }
    return true;
  });

  constructor() {
    effect(() => this.cartService.enterStore(this.storeSlug()));

    // Variant products have exactly one size/color of their own (see backend
    // variantGroupId siblings) - auto-select them so the existing
    // canAddToCart()/addToCart() logic (unchanged) works without special-
    // casing: it already reads selectedSize()/selectedColor() and
    // product.stockQuantity, which is now correctly this specific sibling's.
    effect(() => {
      const product = this.productState().product;
      if (product?.variantGroupId) {
        this.selectedSize.set(product.availableSizes[0] ?? null);
        this.selectedColor.set(product.availableColors[0] ?? null);
      }
    });
  }

  selectSize(size: string): void {
    this.selectedSize.set(size);
  }

  selectColor(color: string): void {
    this.selectedColor.set(color);
  }

  /** Switch to the sibling variant with this color (keeping the current size where possible). */
  selectVariantColor(color: string): void {
    const currentSize = this.selectedSize();
    const target =
      this.productState().variants.find((v) => v.color === color && v.size === currentSize) ??
      this.productState().variants.find((v) => v.color === color);
    if (target) {
      this.router.navigate(['/store', this.storeSlug(), 'products', target.id], { replaceUrl: true });
    }
  }

  /** Switch to the sibling variant with this size (keeping the current color where possible). */
  selectVariantSize(size: string): void {
    const currentColor = this.selectedColor();
    const target =
      this.productState().variants.find((v) => v.size === size && v.color === currentColor) ??
      this.productState().variants.find((v) => v.size === size);
    if (target) {
      this.router.navigate(['/store', this.storeSlug(), 'products', target.id], { replaceUrl: true });
    }
  }

  changeQuantity(delta: number): void {
    const product = this.productState().product;
    const max = product?.stockQuantity ?? 1;
    this.quantity.update((q) => Math.min(Math.max(1, q + delta), Math.max(1, max)));
  }

  addToCart(): void {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }

    const product = this.productState().product;
    if (!product) {
      return;
    }

    this.adding.set(true);
    this.addError.set(null);
    this.addSuccess.set(false);

    this.cartService
      .addItem({
        productId: product.id,
        quantity: this.quantity(),
        size: this.selectedSize() ?? undefined,
        color: this.selectedColor() ?? undefined,
      })
      .subscribe({
        next: () => {
          this.adding.set(false);
          this.addSuccess.set(true);
        },
        error: (err: HttpErrorResponse) => {
          this.adding.set(false);
          this.addError.set(extractErrorMessage(err));
        },
      });
  }
}
