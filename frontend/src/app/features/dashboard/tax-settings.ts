import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { ActionErrorBanner } from './action-error-banner';
import { ActionError, toActionError } from './subscription-error.util';
import { TaxActivity, TaxActivityOption, TaxMethod, TaxPeriodType, TaxProfileDTO } from './tax.models';
import { TaxService } from './tax.service';

/** Same shape as the server's @Pattern on TaxProfileRequest.taxCode - 10 digits, optionally with a 3-digit branch suffix. */
const TAX_CODE_PATTERN = /^[0-9]{10}(-[0-9]{3})?$/;

/**
 * "Thiết lập" under Thuế & Kế toán - who the household business is, and the
 * two choices every figure in this module hangs off: its ngành nghề (which
 * picks the rate pair) and its kỳ kê khai (which picks the period length and
 * the deadline rule).
 *
 * The rates themselves are never typed here, only shown. They are fixed by
 * Phụ lục I, Thông tư 40/2021 - a shop free to type its own would file a
 * wrong return and hear about it from the tax office - so the picker offers
 * the activity and the server answers with the percentages.
 */
@Component({
  selector: 'app-tax-settings',
  standalone: true,
  imports: [ActionErrorBanner, ReactiveFormsModule, VndCurrencyPipe],
  templateUrl: './tax-settings.html',
})
export class TaxSettings {
  private readonly taxService = inject(TaxService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly actionError = signal<ActionError | null>(null);
  readonly savedMessage = signal<string | null>(null);

  readonly activities = signal<TaxActivityOption[]>([]);

  readonly form = this.fb.nonNullable.group({
    businessName: ['', [Validators.maxLength(200)]],
    taxCode: ['', [Validators.pattern(TAX_CODE_PATTERN)]],
    ownerName: ['', [Validators.maxLength(200)]],
    businessAddress: ['', [Validators.maxLength(255)]],
    taxOffice: ['', [Validators.maxLength(200)]],
    taxMethod: ['KE_KHAI' as TaxMethod],
    periodType: ['QUARTER' as TaxPeriodType],
    activity: ['DISTRIBUTION' as TaxActivity],
    exemptThreshold: [0, [Validators.min(0)]],
  });

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  /** The rate pair behind the chosen activity, shown live beside the picker so the consequence of the choice is visible. */
  readonly selectedActivity = computed(() => {
    const chosen = this.formValue().activity;
    return this.activities().find((a) => a.value === chosen) ?? null;
  });

  /** A khoán household files no periodic return, so the kỳ kê khai below has nothing to decide. */
  readonly isLumpSum = computed(() => this.formValue().taxMethod === 'KHOAN');

  constructor() {
    this.taxService.activities().subscribe({
      next: (res) => this.activities.set(res.activities),
      error: () => {},
    });
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.taxService.profile().subscribe({
      next: (profile) => {
        this.loading.set(false);
        this.apply(profile);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(toActionError(err).message);
      },
    });
  }

  private apply(profile: TaxProfileDTO): void {
    this.form.reset({
      businessName: profile.businessName ?? '',
      taxCode: profile.taxCode ?? '',
      ownerName: profile.ownerName ?? '',
      businessAddress: profile.businessAddress ?? '',
      taxOffice: profile.taxOffice ?? '',
      taxMethod: profile.taxMethod,
      periodType: profile.periodType,
      activity: profile.activity,
      exemptThreshold: profile.exemptThreshold,
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.actionError.set(null);
    this.savedMessage.set(null);
    const value = this.form.getRawValue();
    this.taxService
      .saveProfile({
        businessName: value.businessName.trim() || null,
        taxCode: value.taxCode.trim() || null,
        ownerName: value.ownerName.trim() || null,
        businessAddress: value.businessAddress.trim() || null,
        taxOffice: value.taxOffice.trim() || null,
        taxMethod: value.taxMethod,
        periodType: value.periodType,
        activity: value.activity,
        exemptThreshold: value.exemptThreshold,
      })
      .subscribe({
        next: (res) => {
          this.saving.set(false);
          this.apply(res.profile);
          this.savedMessage.set(res.message);
        },
        error: (err: HttpErrorResponse) => {
          this.saving.set(false);
          this.actionError.set(toActionError(err));
        },
      });
  }
}
