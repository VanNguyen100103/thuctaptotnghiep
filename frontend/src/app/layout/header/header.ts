import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';

/** Same list offered on the store-register "Ngành nghề" dropdown - real, not decorative. */
export const MARKETING_INDUSTRIES = [
  'Bán buôn, bán lẻ',
  'Ăn uống, giải trí',
  'Sức khỏe, làm đẹp',
  'Khách sạn, nhà nghỉ',
  'Thời trang',
];

/**
 * "Giải pháp" dropdown - only features this app actually has. KiotViet's own
 * menu also lists e-invoicing, business loans, FoodApp/OTA integration,
 * a website builder, staff payroll/scheduling - none of which exist here,
 * so they're deliberately not copied in.
 */
export const MARKETING_SOLUTIONS = [
  'Storefront & giỏ hàng đa cửa hàng',
  'Thanh toán PayPal / MoMo / COD',
  'Quản lý sản phẩm & tồn kho đa ngành',
  'Dashboard doanh thu & báo cáo',
];

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

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** The marketing nav (Tính năng/Ngành hàng/Bảng giá) only makes sense on the SaaS marketing shell, not while a customer is shopping a specific store's storefront. */
  readonly showMarketingNav = computed(() => !this.currentUrl().startsWith('/store/'));

  logout(): void {
    this.authService.logout();
    this.router.navigateByUrl('/');
  }
}
