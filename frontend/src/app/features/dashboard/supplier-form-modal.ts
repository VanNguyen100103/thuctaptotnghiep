import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ActionErrorBanner } from './action-error-banner';
import { SupplierDTO, SupplierRequest } from './supplier.models';
import { SupplierService } from './supplier.service';
import { ActionError, toActionError } from './subscription-error.util';

/**
 * "Tạo nhà cung cấp" modal, matching KiotViet's real modal layout (user-
 * supplied screenshot) field-for-field. Shared by SupplierList (create/edit
 * from the Nhà cung cấp page) and PurchaseOrderForm (quick-add from the
 * Nhập hàng form's "+" button, replacing the earlier window.prompt()).
 */
@Component({
  selector: 'app-supplier-form-modal',
  standalone: true,
  imports: [ReactiveFormsModule, ActionErrorBanner],
  templateUrl: './supplier-form-modal.html',
})
export class SupplierFormModal {
  private readonly supplierService = inject(SupplierService);
  private readonly fb = inject(FormBuilder);

  readonly open = input.required<boolean>();
  /** null = create mode; a supplier = edit mode. */
  readonly editingSupplier = input<SupplierDTO | null>(null);
  /** Pre-fills "Tên nhà cung cấp" when opened for create (e.g. from a search box's typed text). */
  readonly initialName = input<string>('');

  readonly saved = output<SupplierDTO>();
  readonly closed = output<void>();

  readonly isEditMode = computed(() => this.editingSupplier() !== null);

  readonly addressSectionOpen = signal(true);
  readonly groupNoteSectionOpen = signal(true);
  readonly invoiceSectionOpen = signal(true);

  readonly submitting = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    phone: [''],
    email: [''],
    address: [''],
    region: [''],
    ward: [''],
    groupName: [''],
    note: [''],
    companyName: [''],
    taxCode: [''],
  });

  constructor() {
    // Reset the form fresh every time the modal opens - either from the
    // supplier being edited, or blank (with the search-box prefill) for create.
    effect(() => {
      if (!this.open()) {
        return;
      }
      const editing = this.editingSupplier();
      this.actionError.set(null);
      this.addressSectionOpen.set(true);
      this.groupNoteSectionOpen.set(true);
      this.invoiceSectionOpen.set(true);
      this.form.reset({
        name: editing?.name ?? this.initialName(),
        phone: editing?.phone ?? '',
        email: editing?.email ?? '',
        address: editing?.address ?? '',
        region: editing?.region ?? '',
        ward: editing?.ward ?? '',
        groupName: editing?.groupName ?? '',
        note: editing?.note ?? '',
        companyName: editing?.companyName ?? '',
        taxCode: editing?.taxCode ?? '',
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
    const request: SupplierRequest = {
      name: value.name,
      phone: value.phone || undefined,
      email: value.email || undefined,
      address: value.address || undefined,
      region: value.region || undefined,
      ward: value.ward || undefined,
      groupName: value.groupName || undefined,
      note: value.note || undefined,
      companyName: value.companyName || undefined,
      taxCode: value.taxCode || undefined,
    };
    const editing = this.editingSupplier();
    const call = editing ? this.supplierService.update(editing.id, request) : this.supplierService.create(request);
    call.subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.saved.emit(res.supplier);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }
}
