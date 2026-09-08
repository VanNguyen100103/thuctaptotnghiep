import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { ZaloAuthService } from '../../core/auth/zalo-auth.service';
import { extractErrorMessage } from '../../core/http/api-error';

/**
 * Where Zalo sends the browser back to.
 *
 * Carries no guard on purpose: signing in means arriving here signed out,
 * and linking means arriving here signed in. The intent kept by
 * ZaloAuthService is what tells the two apart - the code Zalo returns looks
 * identical either way.
 */
@Component({
  selector: 'app-zalo-callback',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="flex min-h-screen items-center justify-center px-4">
      <div class="w-full max-w-sm text-center">
        @if (error(); as message) {
          <p class="text-sm text-red-600">{{ message }}</p>
          <a routerLink="/login" class="mt-4 inline-block text-sm text-blue-600 underline">Về trang đăng nhập</a>
        } @else {
          <p class="text-sm text-gray-600">Đang xác thực với Zalo...</p>
        }
      </div>
    </div>
  `,
})
export class ZaloCallback implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly zaloAuth = inject(ZaloAuthService);

  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const code = params.get('code');
    const state = params.get('state');
    const intent = this.zaloAuth.consumeIntent();

    if (!code || !state) {
      // Zalo also lands here when the user declines, without a code.
      this.error.set('Đăng nhập Zalo đã bị hủy hoặc thiếu thông tin trả về.');
      return;
    }

    if (intent === 'link') {
      this.zaloAuth.link(code, state).subscribe({
        next: () => this.router.navigateByUrl('/dashboard/account'),
        error: (err: HttpErrorResponse) => this.error.set(extractErrorMessage(err)),
      });
      return;
    }

    this.authService.loginWithZalo(code, state).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (err: HttpErrorResponse) => this.error.set(extractErrorMessage(err)),
    });
  }
}
