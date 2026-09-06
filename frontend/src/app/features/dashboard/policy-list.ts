import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';

import { ActionErrorBanner } from './action-error-banner';
import { PolicyFormModal } from './policy-form-modal';
import { PolicyDTO } from './policy.models';
import { PolicyService } from './policy.service';
import { ActionError, toActionError } from './subscription-error.util';

@Component({
  selector: 'app-policy-list',
  standalone: true,
  imports: [ActionErrorBanner, PolicyFormModal],
  templateUrl: './policy-list.html',
})
export class PolicyList {
  private readonly policyService = inject(PolicyService);

  readonly policies = signal<PolicyDTO[]>([]);
  readonly loadError = signal<string | null>(null);
  readonly loading = signal(false);

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.policyService.list().subscribe({
      next: (res) => {
        this.loading.set(false);
        this.policies.set(res.policies);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(err.message);
      },
    });
  }

  readonly formOpen = signal(false);
  readonly editingPolicy = signal<PolicyDTO | null>(null);
  readonly actionError = signal<ActionError | null>(null);

  openCreateForm(): void {
    this.editingPolicy.set(null);
    this.formOpen.set(true);
  }

  openEditForm(policy: PolicyDTO): void {
    this.editingPolicy.set(policy);
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
    this.policyService.delete(id).subscribe({
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
