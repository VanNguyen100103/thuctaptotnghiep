import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { StoreProfileService } from '../../core/store/store-profile.service';
import { DashboardTabs } from './dashboard-tabs';

/**
 * The blue chrome above every dashboard page (utility row + tab row), styled
 * after KiotViet's own. It replaces the white marketing header on /dashboard
 * - see App.showHeader - so it also has to carry what that header carried for
 * a signed-in user: the account link, the storefront link and sign-out, all
 * folded into the avatar menu on the right.
 */
@Component({
  selector: 'app-dashboard-header',
  standalone: true,
  imports: [RouterLink, DashboardTabs],
  templateUrl: './dashboard-header.html',
})
export class DashboardHeader {
  private readonly authService = inject(AuthService);
  private readonly storeProfileService = inject(StoreProfileService);
  private readonly router = inject(Router);

  readonly currentUser = this.authService.currentUser;
  readonly storeSlug = signal<string | null>(null);
  private readonly storeName = signal<string | null>(null);

  /** Same gate as Dashboard's: only OWNER/MANAGER have a store on their JWT, so only they get the tab row and the store lookup. */
  readonly isOwnerOrManager = computed(() => {
    const role = this.currentUser()?.storeRole;
    return role === 'OWNER' || role === 'MANAGER';
  });

  /** KiotViet's branch picker. One store is one branch here, so this shows the store's own name and stays disabled. */
  readonly branchName = computed(() => this.storeName() ?? 'Chi nhánh trung tâm');

  readonly accountMenuOpen = signal(false);

  constructor() {
    if (this.isOwnerOrManager()) {
      this.storeProfileService.getCurrentStore().subscribe({
        next: (store) => {
          this.storeSlug.set(store.slug);
          this.storeName.set(store.name);
        },
        error: () => {},
      });
    }
  }

  toggleAccountMenu(): void {
    this.accountMenuOpen.update((open) => !open);
  }

  closeAccountMenu(): void {
    this.accountMenuOpen.set(false);
  }

  /** Closes the avatar menu on any outside click; the menu's wrapper stops propagation so clicks inside it don't reach here. */
  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.accountMenuOpen()) {
      this.closeAccountMenu();
    }
  }

  logout(): void {
    this.closeAccountMenu();
    this.authService.logout();
    this.router.navigateByUrl('/');
  }
}
