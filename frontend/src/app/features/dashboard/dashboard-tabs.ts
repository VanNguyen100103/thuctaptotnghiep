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
  'Thuế & Kế toán',
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

  /**
   * KiotViet draws the active tab as a lighter block standing out of the
   * band. That has to be one whole class list rather than a static class plus
   * `[class.bg-blue-600]`, because Tailwind emits hover: utilities after the
   * plain ones - the hover wash would otherwise win over the active block and
   * the tab would appear to lose its highlight under the pointer.
   */
  tabClass(active: boolean): string {
    const base = 'rounded-t-md whitespace-nowrap px-2.5 py-3 text-[13px] font-medium transition-colors';
    return active ? `${base} bg-blue-600 text-white` : `${base} text-white/80 hover:bg-white/10 hover:text-white`;
  }

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** "Hàng hóa" is a dropdown trigger (like KiotViet) - only "Danh sách hàng hóa" is a real route so far. */
  readonly productsActive = computed(() => this.currentUrl().startsWith('/dashboard/products'));

  readonly productsMenuOpen = signal(false);

  openProductsMenu(): void {
    this.productsMenuOpen.set(true);
  }

  closeProductsMenu(): void {
    this.productsMenuOpen.set(false);
  }

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

  /** "Đặt hàng" - what customers ordered themselves. */
  readonly ordersListActive = computed(() => this.currentUrl().startsWith('/dashboard/orders'));

  /** "Hóa đơn" - what the register sold. */
  readonly invoicesActive = computed(() => this.currentUrl().startsWith('/dashboard/invoices'));

  readonly deliveryPartnersActive = computed(() => this.currentUrl().startsWith('/dashboard/delivery-partners'));

  /** "Đơn hàng" is a dropdown trigger (like KiotViet) listing order-related pages - "Đặt hàng",
   * "Hóa đơn" and "Đối tác giao hàng" are built, the rest stay disabled placeholders. */
  readonly ordersActive = computed(
    () => this.ordersListActive() || this.invoicesActive() || this.deliveryPartnersActive(),
  );

  readonly ordersMenuOpen = signal(false);

  openOrdersMenu(): void {
    this.ordersMenuOpen.set(true);
  }

  closeOrdersMenu(): void {
    this.ordersMenuOpen.set(false);
  }
}
