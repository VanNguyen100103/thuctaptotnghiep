import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { GoogleSignInButton } from '../../core/auth/google-sign-in-button';
import { ZaloAuthService } from '../../core/auth/zalo-auth.service';
import { extractErrorMessage } from '../../core/http/api-error';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, GoogleSignInButton],
  templateUrl: './login.html',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly zaloAuth = inject(ZaloAuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);

  /** No client id means the whole section stays off the page rather than rendering a button that cannot work. */
  readonly googleEnabled = !!environment.googleClientId;
  readonly zaloEnabled = environment.zaloEnabled;

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  /**
   * Google has vouched for an email; the API decides whether it belongs to
   * anyone here. An email with no account is a 404 telling them to register a
   * store first, which is the honest answer - signing in with Google says
   * nothing about which store somebody belongs to.
   */
  onGoogleCredential(idToken: string): void {
    this.formError.set(null);
    this.submitting.set(true);
    this.authService.loginWithGoogle(idToken).subscribe({
      next: () => {
        this.submitting.set(false);
        this.goToReturnUrl();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(extractErrorMessage(err));
      },
    });
  }

  /**
   * Leaves the app entirely: Zalo has no popup flow for the web, so this is
   * a redirect out and back to /auth/zalo/callback.
   */
  loginWithZalo(): void {
    this.formError.set(null);
    this.submitting.set(true);
    this.zaloAuth.startFlow('login').subscribe({
      next: ({ url }) => (window.location.href = url),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(extractErrorMessage(err));
      },
    });
  }

  private goToReturnUrl(): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard';
    this.router.navigateByUrl(returnUrl);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.formError.set(null);
    this.submitting.set(true);

    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.goToReturnUrl();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(extractErrorMessage(err));
      },
    });
  }
}
