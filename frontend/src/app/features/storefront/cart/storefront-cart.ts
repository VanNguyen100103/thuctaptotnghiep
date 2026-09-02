import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { CartService } from '../../../core/cart/cart.service';
import { extractErrorMessage } from '../../../core/http/api-error';
import { VndCurrencyPipe } from '../../../core/currency/vnd-currency.pipe';

@Component({
  selector: 'app-storefront-cart',
  standalone: true,
  imports: [RouterLink, VndCurrencyPipe],
  templateUrl: './storefront-cart.html',
})
export class StorefrontCart {
  private readonly route = inject(ActivatedRoute);
  private readonly cartService = inject(CartService);

  private readonly paramMap = toSignal(this.route.paramMap, { requireSync: true });
  readonly storeSlug = computed(() => this.paramMap()!.get('storeSlug')!);

  readonly cart = this.cartService.cart;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const slug = this.storeSlug();
      this.cartService.enterStore(slug);
      this.loading.set(true);
      this.cartService.loadCart(slug).subscribe({
        next: () => this.loading.set(false),
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(extractErrorMessage(err));
        },
      });
    });
  }

  updateQuantity(itemId: number, quantity: number): void {
    if (quantity < 1) {
      return;
    }
    this.cartService.updateItem(itemId, quantity).subscribe({
      error: (err: HttpErrorResponse) => this.error.set(extractErrorMessage(err)),
    });
  }

  removeItem(itemId: number): void {
    this.cartService.removeItem(itemId).subscribe({
      error: (err: HttpErrorResponse) => this.error.set(extractErrorMessage(err)),
    });
  }

  clearCart(): void {
    this.cartService.clearCart(this.storeSlug()).subscribe({
      error: (err: HttpErrorResponse) => this.error.set(extractErrorMessage(err)),
    });
  }
}
