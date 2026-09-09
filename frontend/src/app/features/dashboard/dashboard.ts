import { Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { DashboardHeader } from './dashboard-header';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DashboardHeader, RouterOutlet],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly authService = inject(AuthService);

  readonly currentUser = this.authService.currentUser;

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
}
