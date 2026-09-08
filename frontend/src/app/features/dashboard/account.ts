import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { ZaloAuthService } from '../../core/auth/zalo-auth.service';
import { extractErrorMessage } from '../../core/http/api-error';

/**
 * "Tài khoản" - where a signed-in user manages how they get back in.
 *
 * It exists because Zalo needs somewhere to be linked from. Zalo returns no
 * email and no phone, only an id scoped to this app, so it cannot recognise
 * anyone until an account has claimed that id once from here.
 */
@Component({
  selector: 'app-account',
  standalone: true,
  templateUrl: './account.html',
})
export class Account implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly zaloAuth = inject(ZaloAuthService);

  readonly currentUser = this.authService.currentUser;

  readonly linked = signal(false);
  readonly available = signal(false);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  ngOnInit(): void {
    this.zaloAuth.status().subscribe({
      next: (status) => {
        this.loading.set(false);
        this.linked.set(status.linked);
        // Whether the deployment has Zalo credentials at all - a link button
        // that cannot work is worse than none.
        this.available.set(status.available);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(extractErrorMessage(err));
      },
    });
  }

  /** Leaves the app: Zalo has no popup flow for the web, so this is a full redirect out and back. */
  linkZalo(): void {
    this.busy.set(true);
    this.error.set(null);
    this.zaloAuth.startFlow('link').subscribe({
      next: ({ url }) => (window.location.href = url),
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(extractErrorMessage(err));
      },
    });
  }

  unlinkZalo(): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.zaloAuth.unlink().subscribe({
      next: () => {
        this.busy.set(false);
        this.linked.set(false);
        this.notice.set('Đã hủy liên kết Zalo.');
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(extractErrorMessage(err));
      },
    });
  }
}
