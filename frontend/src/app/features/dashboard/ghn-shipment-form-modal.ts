import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ActionErrorBanner } from './action-error-banner';
import { CreateGhnShipmentRequest, GhnLocationOption, GhnShipmentDTO } from './ghn-shipment.models';
import { GhnShipmentService } from './ghn-shipment.service';
import { ActionError, toActionError } from './subscription-error.util';

/**
 * "+ Tạo đơn test" modal - creates a real GHN sandbox shipment. Province ->
 * District -> Ward are cascading selects sourced live from GHN's own
 * master-data API (GHN requires numeric district_id/ward_code, not free-text
 * city/state - see GhnShipment's backend doc comment), each reset whenever
 * its parent changes.
 */
@Component({
  selector: 'app-ghn-shipment-form-modal',
  standalone: true,
  imports: [ReactiveFormsModule, ActionErrorBanner],
  templateUrl: './ghn-shipment-form-modal.html',
})
export class GhnShipmentFormModal {
  private readonly ghnShipmentService = inject(GhnShipmentService);
  private readonly fb = inject(FormBuilder);

  readonly open = input.required<boolean>();
  readonly saved = output<GhnShipmentDTO>();
  readonly closed = output<void>();

  readonly submitting = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  readonly provinces = signal<GhnLocationOption[]>([]);
  readonly districts = signal<GhnLocationOption[]>([]);
  readonly wards = signal<GhnLocationOption[]>([]);
  readonly loadingProvinces = signal(false);
  readonly loadingDistricts = signal(false);
  readonly loadingWards = signal(false);

  readonly form = this.fb.nonNullable.group({
    toName: ['', [Validators.required, Validators.maxLength(200)]],
    toPhone: ['', [Validators.required, Validators.maxLength(30)]],
    toAddress: ['', [Validators.required, Validators.maxLength(500)]],
    toProvinceId: ['', Validators.required],
    toDistrictId: ['', Validators.required],
    toWardCode: ['', Validators.required],
    weightGrams: [500, [Validators.required, Validators.min(1)]],
    note: [''],
  });

  constructor() {
    // Reset to a blank form and (re)load the province list fresh every time the modal opens.
    effect(() => {
      if (!this.open()) {
        return;
      }
      this.actionError.set(null);
      this.districts.set([]);
      this.wards.set([]);
      this.form.reset({ toName: '', toPhone: '', toAddress: '', toProvinceId: '', toDistrictId: '', toWardCode: '', weightGrams: 500, note: '' });
      if (this.provinces().length === 0) {
        this.loadingProvinces.set(true);
        this.ghnShipmentService.provinces().subscribe({
          next: (res) => {
            this.loadingProvinces.set(false);
            this.provinces.set(res.provinces);
          },
          error: (err: HttpErrorResponse) => {
            this.loadingProvinces.set(false);
            this.actionError.set(toActionError(err));
          },
        });
      }
    });
  }

  onProvinceChange(event: Event): void {
    const provinceId = (event.target as HTMLSelectElement).value;
    this.form.patchValue({ toDistrictId: '', toWardCode: '' });
    this.districts.set([]);
    this.wards.set([]);
    if (!provinceId) {
      return;
    }
    this.loadingDistricts.set(true);
    this.ghnShipmentService.districts(provinceId).subscribe({
      next: (res) => {
        this.loadingDistricts.set(false);
        this.districts.set(res.districts);
      },
      error: (err: HttpErrorResponse) => {
        this.loadingDistricts.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  onDistrictChange(event: Event): void {
    const districtId = (event.target as HTMLSelectElement).value;
    this.form.patchValue({ toWardCode: '' });
    this.wards.set([]);
    if (!districtId) {
      return;
    }
    this.loadingWards.set(true);
    this.ghnShipmentService.wards(districtId).subscribe({
      next: (res) => {
        this.loadingWards.set(false);
        this.wards.set(res.wards);
      },
      error: (err: HttpErrorResponse) => {
        this.loadingWards.set(false);
        this.actionError.set(toActionError(err));
      },
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
    const value = this.form.getRawValue();
    const province = this.provinces().find((p) => p.id === value.toProvinceId);
    const district = this.districts().find((d) => d.id === value.toDistrictId);
    const ward = this.wards().find((w) => w.id === value.toWardCode);
    if (!province || !district || !ward) {
      this.actionError.set({ message: 'Vui lòng chọn đầy đủ Tỉnh/Quận/Phường.', isUpgradeRequired: false });
      return;
    }

    this.submitting.set(true);
    this.actionError.set(null);
    const request: CreateGhnShipmentRequest = {
      toName: value.toName,
      toPhone: value.toPhone,
      toAddress: value.toAddress,
      toProvinceId: Number(province.id),
      toProvinceName: province.name,
      toDistrictId: Number(district.id),
      toDistrictName: district.name,
      toWardCode: ward.id,
      toWardName: ward.name,
      weightGrams: value.weightGrams,
      note: value.note || undefined,
    };
    this.ghnShipmentService.create(request).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.ghnShipmentService.notifyChanged();
        this.saved.emit(res.shipment);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }
}
