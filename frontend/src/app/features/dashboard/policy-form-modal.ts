import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ActionErrorBanner } from './action-error-banner';
import { PolicyDTO, PolicyRequest } from './policy.models';
import { PolicyService } from './policy.service';
import { ActionError, toActionError } from './subscription-error.util';

/**
 * "Tạo chính sách" modal - free-named title + content, no fixed type list,
 * so any industry can write whatever policy matters to them (đổi trả, vận
 * chuyển, đặt bàn, ...). Read by the storefront AI chat (ChatToolExecutor)
 * so it answers customers using this text.
 */
@Component({
  selector: 'app-policy-form-modal',
  standalone: true,
  imports: [ReactiveFormsModule, ActionErrorBanner],
  templateUrl: './policy-form-modal.html',
})
export class PolicyFormModal {
  private readonly policyService = inject(PolicyService);
  private readonly fb = inject(FormBuilder);

  readonly open = input.required<boolean>();
  /** null = create mode; a policy = edit mode. */
  readonly editingPolicy = input<PolicyDTO | null>(null);

  readonly saved = output<PolicyDTO>();
  readonly closed = output<void>();

  readonly isEditMode = computed(() => this.editingPolicy() !== null);

  readonly submitting = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    content: ['', [Validators.required, Validators.maxLength(5000)]],
  });

  constructor() {
    effect(() => {
      if (!this.open()) {
        return;
      }
      const editing = this.editingPolicy();
      this.actionError.set(null);
      this.form.reset({
        title: editing?.title ?? '',
        content: editing?.content ?? '',
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
    const request: PolicyRequest = { title: value.title, content: value.content };
    const editing = this.editingPolicy();
    const call = editing ? this.policyService.update(editing.id, request) : this.policyService.create(request);
    call.subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.saved.emit(res.policy);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }
}
