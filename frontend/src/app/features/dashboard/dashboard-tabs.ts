import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map } from 'rxjs';

const UPCOMING_TABS = [
  'Khách hàng',
  'Nhân viên',
  'Sổ quỹ',
  'Báo cáo',
  'Bán online',
];

@Component({
  selector: 'app-dashboard-tabs',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './dashboard-tabs.html',
})
export class DashboardTabs {
  private readonly router = inject(Router);

  readonly upcomingTabs = UPCOMING_TABS;

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** "Mua hàng" is a dropdown trigger (like KiotViet), not a direct link - real routes live inside it (Nhà cung cấp, Nhập hàng). Active-highlighted while on either. */
  readonly purchasingActive = computed(
    () => this.currentUrl().startsWith('/dashboard/purchase-orders') || this.currentUrl().startsWith('/dashboard/suppliers'),
  );

  readonly purchasingMenuOpen = signal(false);

  openPurchasingMenu(): void {
    this.purchasingMenuOpen.set(true);
  }

  closePurchasingMenu(): void {
    this.purchasingMenuOpen.set(false);
  }

  /** "Đơn hàng" is a dropdown trigger (like KiotViet) listing order-related pages - only
   * "Đối tác giao hàng" is built so far, the rest stay disabled placeholders. */
  readonly ordersActive = computed(() => this.currentUrl().startsWith('/dashboard/delivery-partners'));

  readonly ordersMenuOpen = signal(false);

  openOrdersMenu(): void {
    this.ordersMenuOpen.set(true);
  }

  closeOrdersMenu(): void {
    this.ordersMenuOpen.set(false);
  }
}
