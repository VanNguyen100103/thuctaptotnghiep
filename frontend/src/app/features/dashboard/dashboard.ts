import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { StoreProfileService } from '../../core/store/store-profile.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly authService = inject(AuthService);
  private readonly storeProfileService = inject(StoreProfileService);

  readonly currentUser = this.authService.currentUser;
  readonly storeSlug = signal<string | null>(null);

  constructor() {
    // Only OWNER/MANAGER have a store bound to their JWT - a plain customer
    // landing on /dashboard has no storeId and GET /store would just 403.
    if (this.currentUser()?.storeRole === 'OWNER' || this.currentUser()?.storeRole === 'MANAGER') {
      this.storeProfileService.getCurrentStore().subscribe({
        next: (store) => this.storeSlug.set(store.slug),
        error: () => {},
      });
    }
  }
}
