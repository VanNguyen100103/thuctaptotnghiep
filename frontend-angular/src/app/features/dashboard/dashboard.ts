import { Component, inject } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly authService = inject(AuthService);

  readonly currentUser = this.authService.currentUser;
}
