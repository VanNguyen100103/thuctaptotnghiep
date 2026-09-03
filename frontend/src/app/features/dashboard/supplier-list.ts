import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ActionErrorBanner } from './action-error-banner';
import { SupplierDTO } from './supplier.models';
import { SupplierService } from './supplier.service';
import { ActionError, toActionError } from './subscription-error.util';

@Component({
  selector: 'app-supplier-list',
  standalone: true,
  imports: [ReactiveFormsModule, ActionErrorBanner],
  templateUrl: './supplier-list.html',
})
export class SupplierList {
  private readonly supplierService = inject(SupplierService);
  private readonly fb = inject(FormBuilder);

  readonly refreshTick = signal(0);
  private refresh(): void {
    this.refreshTick.update((t) => t + 1);
  }

  readonly searchQuery = signal('');

  // Simple: no toApiState/toObservable plumbing needed for a small dataset -
  // re-fetch directly (see load()) whenever the query or refreshTick changes,
  // rather than wiring a reactive pipe like ProductList's pageState.
  readonly suppliers = signal<SupplierDTO[]>([]);
  readonly loadError = signal<string | null>(null);
  readonly loading = signal(false);

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.supplierService.list(this.searchQuery().trim() || undefined).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.suppliers.set(res.suppliers);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(err.message);
      },
    });
  }

  onSearchInput(event: Event): void {
    this.searchQuery.set((event.target as HTMLInputElement).value);
    this.load();
  }

  readonly formOpen = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly actionError = signal<ActionError | null>(null);
  readonly submitting = signal(false);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    phone: [''],
    email: [''],
    address: [''],
    taxCode: [''],
    note: [''],
  });

  openCreateForm(): void {
    this.editingId.set(null);
    this.form.reset({ name: '', phone: '', email: '', address: '', taxCode: '', note: '' });
    this.formOpen.set(true);
  }

  openEditForm(supplier: SupplierDTO): void {
    this.editingId.set(supplier.id);
    this.form.reset({
      name: supplier.name,
      phone: supplier.phone ?? '',
      email: supplier.email ?? '',
      address: supplier.address ?? '',
      taxCode: supplier.taxCode ?? '',
      note: supplier.note ?? '',
    });
    this.formOpen.set(true);
  }

  closeForm(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    const value = this.form.getRawValue();
    const request = {
      name: value.name,
      phone: value.phone || undefined,
      email: value.email || undefined,
      address: value.address || undefined,
      taxCode: value.taxCode || undefined,
      note: value.note || undefined,
    };
    const editingId = this.editingId();
    const call = editingId !== null ? this.supplierService.update(editingId, request) : this.supplierService.create(request);
    call.subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeForm();
        this.refresh();
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  readonly confirmingDeleteId = signal<number | null>(null);

  requestDelete(id: number): void {
    this.confirmingDeleteId.set(id);
  }

  cancelDelete(): void {
    this.confirmingDeleteId.set(null);
  }

  confirmDelete(id: number): void {
    this.actionError.set(null);
    this.supplierService.delete(id).subscribe({
      next: () => {
        this.confirmingDeleteId.set(null);
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.confirmingDeleteId.set(null);
        this.actionError.set(toActionError(err));
      },
    });
  }
}
