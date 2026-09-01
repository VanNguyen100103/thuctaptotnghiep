import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { extractErrorMessage } from '../../core/http/api-error';

@Component({
  selector: 'app-verify-otp',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './verify-otp.html',
})
export class VerifyOtp {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);
  readonly verified = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: [this.route.snapshot.queryParamMap.get('email') ?? '', [Validators.required, Validators.email]],
    otpCode: ['', [Validators.required, Validators.pattern(/^[0-9]{6}$/)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.formError.set(null);
    this.submitting.set(true);

    this.authService.verifyOtp(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.verified.set(true);
        setTimeout(() => this.router.navigateByUrl('/login'), 1500);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(extractErrorMessage(err));
      },
    });
  }
}
