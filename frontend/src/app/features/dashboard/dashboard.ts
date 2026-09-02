import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { StoreProfileService } from '../../core/store/store-profile.service';
import { DashboardTabs } from './dashboard-tabs';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, DashboardTabs, RouterOutlet],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly authService = inject(AuthService);
  private readonly storeProfileService = inject(StoreProfileService);

  readonly currentUser = this.authService.currentUser;
  readonly storeSlug = signal<string | null>(null);

  // Only OWNER/MANAGER have a store bound to their JWT - a plain customer or
  // STAFF landing on /dashboard has no store-scoped access, and the overview
  // widgets below would just 403. Gating here (not inside DashboardOverview)
  // matters because its toSignal(...) fields are field initializers that fire
  // before any constructor-body guard could run - the only way to actually
  // prevent the HTTP calls is to not instantiate the component at all.
  readonly isOwnerOrManager = computed(() => {
    const role = this.currentUser()?.storeRole;
    return role === 'OWNER' || role === 'MANAGER';
  });

  constructor() {
    if (this.isOwnerOrManager()) {
      this.storeProfileService.getCurrentStore().subscribe({
        next: (store) => this.storeSlug.set(store.slug),
        error: () => {},
      });
    }
  }
}
