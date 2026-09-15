import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { StoreProfileService } from '../../core/store/store-profile.service';
import { DashboardHeader } from './dashboard-header';
import { TaxService } from './tax.service';

/**
 * The shell of "Thuế & Kế toán" - the dashboard chrome above, and a left rail
 * of accounting sections beside the page, the way KiotViet draws this module.
 *
 * Registered as a sibling of /dashboard rather than one of its children,
 * because Dashboard wraps its outlet in a centred max-w-6xl column and this
 * module is full-bleed: the rail has to sit against the left edge of the
 * viewport while the blue chrome above it keeps its own centred column.
 * Reusing DashboardHeader is what keeps the tab row identical, and the tab's
 * own active state is computed from the URL, so it lights up from out here
 * with nothing passed down.
 *
 * Most rail entries are disabled placeholders. They are drawn rather than
 * hidden for the same reason the Hàng hóa menu draws "Kiểm kho": the rail is
 * the map of what this module will hold, and a shop that cannot see Phiếu thu
 * chi coming has no way to know it is on the way.
 */
@Component({
  selector: 'app-tax-shell',
  standalone: true,
  imports: [DashboardHeader, RouterLink, RouterOutlet],
  templateUrl: './tax-shell.html',
})
export class TaxShell {
  private readonly router = inject(Router);
  private readonly taxService = inject(TaxService);
  private readonly storeProfileService = inject(StoreProfileService);

  private readonly businessName = signal<string | null>(null);
  private readonly taxCode = signal<string | null>(null);

  /** The rail's header box: the household's registered name, falling back to the shop sign until Thiết lập is filled in. */
  readonly headerName = computed(() => this.businessName() ?? 'Hộ kinh doanh');
  readonly headerTaxCode = computed(() => this.taxCode() ?? 'Chưa có mã số thuế');

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  readonly declarationsActive = computed(() => this.currentUrl().startsWith('/dashboard/tax/declarations/01-cnkd'));
  readonly settingsActive = computed(() => this.currentUrl().startsWith('/dashboard/tax/settings'));

  // Each rail group collapses, as KiotViet's chevrons do. Open by default:
  // the module has few enough entries to show at once, and a rail that
  // remembered nothing but opened closed would hide the only two screens
  // that work.
  private readonly collapsed = signal<Record<string, boolean>>({});

  isCollapsed(group: string): boolean {
    return this.collapsed()[group] === true;
  }

  toggleGroup(group: string): void {
    this.collapsed.update((state) => ({ ...state, [group]: !state[group] }));
  }

  constructor() {
    // Reloads on every save in Thiết lập too - the rail's header box prints
    // the registered name and MST, so leaving it stale would show the old
    // taxpayer beside the new figures.
    effect(() => {
      this.taxService.changed();
      this.loadProfile();
    });
  }

  private loadProfile(): void {
    this.taxService.profile().subscribe({
      next: (profile) => {
        this.businessName.set(profile.businessName);
        this.taxCode.set(profile.taxCode);
        if (!profile.businessName) {
          // Nothing typed in Thiết lập yet - show the shop's own name rather
          // than a bare "Hộ kinh doanh" the owner cannot recognise.
          this.storeProfileService.getCurrentStore().subscribe({
            next: (store) => this.businessName.set(store.name),
            error: () => {},
          });
        }
      },
      error: () => {},
    });
  }
}
