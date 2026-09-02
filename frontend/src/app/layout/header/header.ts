import { Component, computed, inject, signal } from '@angular/core';
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

/** Which marketing-nav mega-menu is currently hovered open (null = none). */
export type MarketingMenu = 'solutions' | 'industries';

export interface MarketingSolutionItem {
  label: string;
  free?: boolean;
}

export interface MarketingSolutionColumn {
  title: string;
  items: MarketingSolutionItem[];
}

/**
 * Mirrors KiotViet's own "Giải pháp" mega-menu columns/labels verbatim for
 * visual parity - this is a KiotViet UI clone built as a portfolio piece, so
 * the labels (e-invoicing, business loans, FoodApp/OTA, payroll...) are
 * marketing copy for layout fidelity, not a claim that this app implements
 * every one of them.
 */
export const MARKETING_SOLUTIONS: MarketingSolutionColumn[] = [
  {
    title: 'Bán hàng',
    items: [
      { label: 'Bán buôn, bán lẻ' },
      { label: 'Ăn uống, giải trí' },
      { label: 'Sức khỏe, làm đẹp' },
      { label: 'Khách sạn, nhà nghỉ' },
    ],
  },
  {
    title: 'Kế toán & Thuế',
    items: [
      { label: 'Kế toán hộ kinh doanh', free: true },
      { label: 'Hoá đơn điện tử', free: true },
      { label: 'Tư vấn thuế' },
    ],
  },
  {
    title: 'Tài chính',
    items: [{ label: 'Giải pháp thanh toán QR' }, { label: 'Giải pháp vay vốn kinh doanh' }],
  },
  {
    title: 'Bán hàng Online',
    items: [
      { label: 'Đồng bộ sàn TMĐT và mạng xã hội' },
      { label: 'Tích hợp FoodApp' },
      { label: 'Tích hợp OTA' },
      { label: 'Tạo website bán hàng' },
      { label: 'Giải pháp giao hàng' },
    ],
  },
  {
    title: 'Quản lý nhân viên',
    items: [
      { label: 'Bảng chấm công' },
      { label: 'Bảng tính lương' },
      { label: 'Lịch làm việc' },
      { label: 'Bảng hoa hồng' },
    ],
  },
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

  /** The marketing nav (Tính năng/Ngành hàng/Bảng giá) only makes sense on the SaaS marketing shell, not while a customer is shopping a specific store's storefront. */
  readonly showMarketingNav = computed(() => !this.currentUrl().startsWith('/store/'));

  logout(): void {
    this.authService.logout();
    this.router.navigateByUrl('/');
  }
}
