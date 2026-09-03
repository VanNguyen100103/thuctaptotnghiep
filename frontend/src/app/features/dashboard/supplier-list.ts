import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';

import { ActionErrorBanner } from './action-error-banner';
import { SupplierFormModal } from './supplier-form-modal';
import { SupplierDTO } from './supplier.models';
import { SupplierService } from './supplier.service';
import { ActionError, toActionError } from './subscription-error.util';

@Component({
  selector: 'app-supplier-list',
  standalone: true,
  imports: [ActionErrorBanner, SupplierFormModal],
  templateUrl: './supplier-list.html',
})
export class SupplierList {
  private readonly supplierService = inject(SupplierService);

  readonly searchQuery = signal('');

  // Simple: no toApiState/toObservable plumbing needed for a small dataset -
  // re-fetch directly (see load()) whenever the query changes, rather than
  // wiring a reactive pipe like ProductList's pageState.
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
  readonly editingSupplier = signal<SupplierDTO | null>(null);
  readonly actionError = signal<ActionError | null>(null);

  openCreateForm(): void {
    this.editingSupplier.set(null);
    this.formOpen.set(true);
  }

  openEditForm(supplier: SupplierDTO): void {
    this.editingSupplier.set(supplier);
    this.formOpen.set(true);
  }

  closeForm(): void {
    this.formOpen.set(false);
  }

  onSaved(): void {
    this.formOpen.set(false);
    this.load();
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
