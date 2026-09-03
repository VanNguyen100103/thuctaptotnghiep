import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ActionErrorBanner } from './action-error-banner';
import { CustomerDTO, CustomerRequest } from './customer.models';
import { CustomerService } from './customer.service';
import { ActionError, toActionError } from './subscription-error.util';

/**
 * "Thêm khách hàng" quick-add modal, opened from the Bán hàng (POS) screen's
 * "+" next to "Tìm khách hàng (F4)" - matches KiotViet's real modal layout
 * (user-supplied screenshot) for the fields this app actually models. Only
 * "Tên khách hàng" is required, same as the real thing; "Mã khách hàng" is
 * always server-generated. Facebook/"Người phụ trách"/cascading Tỉnh-Huyện
 * pickers are left out - no staff-assignment or province/district dataset
 * exists anywhere else in this app to back them (same scope-cut reasoning
 * as SupplierFormModal).
 */
@Component({
  selector: 'app-customer-form-modal',
  standalone: true,
  imports: [ReactiveFormsModule, ActionErrorBanner],
  templateUrl: './customer-form-modal.html',
})
export class CustomerFormModal {
  private readonly customerService = inject(CustomerService);
  private readonly fb = inject(FormBuilder);

  readonly open = input.required<boolean>();
  /** Pre-fills "Tên khách hàng" when opened (e.g. from a search box's typed text). */
  readonly initialName = input<string>('');

  readonly saved = output<CustomerDTO>();
  readonly closed = output<void>();

  readonly submitting = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    phone: [''],
    email: [''],
    dateOfBirth: [''],
    gender: [''],
    address: [''],
    region: [''],
    ward: [''],
    groupName: [''],
    note: [''],
  });

  constructor() {
    effect(() => {
      if (!this.open()) {
        return;
      }
      this.actionError.set(null);
      this.form.reset({
        name: this.initialName(),
        phone: '',
        email: '',
        dateOfBirth: '',
        gender: '',
        address: '',
        region: '',
        ward: '',
        groupName: '',
        note: '',
      });
    });
  }

  cancel(): void {
    this.closed.emit();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    const value = this.form.getRawValue();
    const request: CustomerRequest = {
      name: value.name,
      phone: value.phone || undefined,
      email: value.email || undefined,
      dateOfBirth: value.dateOfBirth || undefined,
      gender: value.gender || undefined,
      address: value.address || undefined,
      region: value.region || undefined,
      ward: value.ward || undefined,
      groupName: value.groupName || undefined,
      note: value.note || undefined,
    };
    this.customerService.create(request).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.saved.emit(res.customer);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }
}
