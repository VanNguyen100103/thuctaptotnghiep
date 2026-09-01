import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, filter, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { AddCartItemRequest, Cart, CartItem } from './cart.models';

/**
 * Cart is login-gated server-side (no guest cart) and scoped per (user,
 * store) - a customer can shop multiple stores, each with its own cart.
 * Mirrors AuthService's signal pattern.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly cartSignal = signal<Cart | null>(null);
  private readonly countSignal = signal(0);
  private readonly activeStoreSlugSignal = signal<string | null>(null);

  readonly cart = this.cartSignal.asReadonly();
  readonly itemCount = this.countSignal.asReadonly();
  readonly activeStoreSlug = this.activeStoreSlugSignal.asReadonly();
  readonly isEmpty = computed(() => this.countSignal() === 0);

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService,
    router: Router,
  ) {
    router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd)).subscribe((event) => {
      if (!event.urlAfterRedirects.startsWith('/store/')) {
        this.activeStoreSlugSignal.set(null);
      }
    });
  }

  /**
   * Called by every storefront-scoped component on init to tell the header
   * which store's cart to link to. Cart itself requires login, so this is a
   * safe no-op for guests.
   */
  enterStore(storeSlug: string): void {
    this.activeStoreSlugSignal.set(storeSlug);
    if (!this.authService.isAuthenticated()) {
      return;
    }
    this.refreshCount(storeSlug).subscribe({ error: () => {} });
  }

  refreshCount(storeSlug: string): Observable<{ count: number }> {
    return this.http
      .get<{ count: number }>(`${environment.apiUrl}/cart/count`, { params: { storeSlug } })
      .pipe(tap((res) => this.countSignal.set(res.count)));
  }

  loadCart(storeSlug: string): Observable<Cart> {
    this.activeStoreSlugSignal.set(storeSlug);
    return this.http
      .get<Cart>(`${environment.apiUrl}/cart`, { params: { storeSlug } })
      .pipe(tap((cart) => this.applyCart(cart)));
  }

  addItem(request: AddCartItemRequest): Observable<{ message: string; cart: Cart; itemAdded: CartItem }> {
    return this.http
      .post<{ message: string; cart: Cart; itemAdded: CartItem }>(`${environment.apiUrl}/cart/items`, request)
      .pipe(tap((res) => this.applyCart(res.cart)));
  }

  updateItem(itemId: number, quantity: number): Observable<{ message: string; cart: Cart; itemUpdated: CartItem }> {
    return this.http
      .put<{ message: string; cart: Cart; itemUpdated: CartItem }>(`${environment.apiUrl}/cart/items/${itemId}`, {
        quantity,
      })
      .pipe(tap((res) => this.applyCart(res.cart)));
  }

  removeItem(itemId: number): Observable<{ message: string; cart: Cart }> {
    return this.http
      .delete<{ message: string; cart: Cart }>(`${environment.apiUrl}/cart/items/${itemId}`)
      .pipe(tap((res) => this.applyCart(res.cart)));
  }

  clearCart(storeSlug: string): Observable<{ message: string; cart: Cart }> {
    return this.http
      .delete<{ message: string; cart: Cart }>(`${environment.apiUrl}/cart/clear`, { params: { storeSlug } })
      .pipe(tap((res) => this.applyCart(res.cart)));
  }

  /**
   * Backend already clears the cart server-side as part of /orders/checkout
   * succeeding - this just zeroes local state immediately so the header
   * badge doesn't lag a refetch.
   */
  clearLocal(): void {
    this.cartSignal.set(null);
    this.countSignal.set(0);
  }

  private applyCart(cart: Cart): void {
    this.cartSignal.set(cart);
    this.countSignal.set(cart.totalItems);
  }
}
