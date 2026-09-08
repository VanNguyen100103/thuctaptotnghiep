import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { MARKETING_INDUSTRIES, MARKETING_SOLUTIONS } from '../../core/marketing/marketing.data';

/** Which marketing-nav mega-menu is currently hovered open (null = none). */
export type MarketingMenu = 'solutions' | 'industries';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './header.html',
})
export class Header {
  private readonly authService = inject(AuthService);
  private readonly cartService = inject(CartService);
  private readonly router = inject(Router);

  readonly currentUser = this.authService.currentUser;
  readonly cartItemCount = this.cartService.itemCount;
  readonly activeStoreSlug = this.cartService.activeStoreSlug;
  readonly industries = MARKETING_INDUSTRIES;
  readonly solutions = MARKETING_SOLUTIONS;

  /** Drives the mega-menu panel + the page-dimming backdrop behind it, KiotViet-style. */
  readonly activeMenu = signal<MarketingMenu | null>(null);

  openMenu(menu: MarketingMenu): void {
    this.activeMenu.set(menu);
  }

  closeMenu(): void {
    this.activeMenu.set(null);
  }

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** The marketing nav only makes sense on the SaaS marketing shell, not while a customer is shopping a specific store's storefront. */
  readonly showMarketingNav = computed(() => !this.currentUrl().startsWith('/store/'));

  /**
   * The landing page runs on a much wider grid than the dashboard, so the bar
   * above it stretches to match; signed in, it stays over the dashboard's own
   * narrower column.
   */
  readonly marketingShell = computed(() => this.showMarketingNav() && !this.currentUser());

  logout(): void {
    this.authService.logout();
    this.router.navigateByUrl('/');
  }
}
