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

  /** More than 1 sibling means this product has real variants (see backend variantGroupId) - free-named attribute axes, not hardcoded to color/size. */
  readonly hasVariants = computed(() => this.productState().variants.length > 1);
  /** Attribute names in the order they appear on the first sibling, e.g. ["Kích cỡ","Màu sắc"] or just ["Hương vị"]. */
  readonly variantAttributeNames = computed(() => Object.keys(this.productState().variants[0]?.attributes ?? {}));
  /** The currently-viewed sibling's own attribute values - the single source of truth for "what's selected" (no separate picker-state signal needed). */
  readonly currentVariantSelection = computed<Record<string, string>>(
    () => this.productState().product?.attributes ?? {},
  );

  /** Distinct values for one attribute, filtered to siblings matching the already-selected values of every EARLIER attribute (progressive narrowing, same idea as the old color->size dependency). */
  variantValuesForAttribute(attributeName: string): string[] {
    const names = this.variantAttributeNames();
    const priorNames = names.slice(0, names.indexOf(attributeName));
    const selected = this.currentVariantSelection();
    const matches = this.productState().variants.filter((v) =>
      priorNames.every((n) => v.attributes[n] === selected[n]),
    );
    return Array.from(new Set(matches.map((v) => v.attributes[attributeName]))).filter((v): v is string => !!v);
  }

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

  /** Switch to the sibling variant matching this attribute value, preserving the current selection on every other axis where possible. */
  selectVariantAttribute(attributeName: string, value: string): void {
    const desired = { ...this.currentVariantSelection(), [attributeName]: value };
    const names = this.variantAttributeNames();
    const target =
      this.productState().variants.find((v) => names.every((n) => v.attributes[n] === desired[n])) ??
      this.productState().variants.find((v) => v.attributes[attributeName] === value);
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
