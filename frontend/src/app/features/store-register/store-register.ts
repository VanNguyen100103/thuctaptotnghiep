import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { extractErrorMessage } from '../../core/http/api-error';
import { StoreRegisterService } from './store-register.service';

const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

@Component({
  selector: 'app-store-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './store-register.html',
})
export class StoreRegister {
  private readonly fb = inject(FormBuilder);
  private readonly storeRegisterService = inject(StoreRegisterService);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    storeName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    storeSlug: [
      '',
      [Validators.required, Validators.pattern(SLUG_PATTERN), Validators.minLength(2), Validators.maxLength(100)],
    ],
    storePhone: [''],
    storeAddress: [''],
    storeIndustry: [''],
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(100)]],
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    phoneNumber: [''],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.formError.set(null);
    this.submitting.set(true);

    this.storeRegisterService.register(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        const email = this.form.getRawValue().email;
        this.router.navigate(['/verify-otp'], { queryParams: { email } });
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        const validationErrors = err.error?.validationErrors as Record<string, string> | undefined;
        if (validationErrors) {
          for (const [field, message] of Object.entries(validationErrors)) {
            this.form.get(field)?.setErrors({ server: message });
          }
        } else {
          this.formError.set(extractErrorMessage(err));
        }
      },
    });
  }
}
