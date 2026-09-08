import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { ActionErrorBanner } from './action-error-banner';
import { CreateShipmentRequest, LocationOption, ShipmentDTO, ShippingRate } from './shipment.models';
import { ShipmentService } from './shipment.service';
import { ActionError, toActionError } from './subscription-error.util';

/**
 * "+ Tạo vận đơn" modal. Tỉnh -> Quận -> Phường cascade live off Goship's
 * own address data, each reset whenever its parent changes.
 *
 * Booking takes two steps here where the old GHN form took one, and that is
 * the whole point of the aggregator: Goship prices a route with every
 * carrier at once, and a booking is made against the rate that was chosen,
 * not against an address and a weight. So the form quotes first, shows what
 * each carrier wants, and only then can be submitted.
 *
 * A quote is invalidated whenever anything it was priced on changes -
 * booking against a rate for a different parcel would either be rejected by
 * Goship or, worse, quietly ship at a price nobody agreed to.
 */
@Component({
  selector: 'app-shipment-form-modal',
  standalone: true,
  imports: [ReactiveFormsModule, ActionErrorBanner, VndCurrencyPipe],
  templateUrl: './shipment-form-modal.html',
})
export class ShipmentFormModal {
  private readonly shipmentService = inject(ShipmentService);
  private readonly fb = inject(FormBuilder);

  readonly open = input.required<boolean>();
  readonly saved = output<ShipmentDTO>();
  readonly closed = output<void>();

  readonly submitting = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  readonly cities = signal<LocationOption[]>([]);
  readonly districts = signal<LocationOption[]>([]);
  readonly wards = signal<LocationOption[]>([]);
  readonly loadingCities = signal(false);
  readonly loadingDistricts = signal(false);
  readonly loadingWards = signal(false);

  readonly rates = signal<ShippingRate[]>([]);
  readonly quoting = signal(false);
  readonly selectedRateId = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    toName: ['', [Validators.required, Validators.maxLength(200)]],
    toPhone: ['', [Validators.required, Validators.maxLength(30)]],
    toAddress: ['', [Validators.required, Validators.maxLength(500)]],
    toCityId: ['', Validators.required],
    toDistrictId: ['', Validators.required],
    toWardId: ['', Validators.required],
    weightGrams: [500, [Validators.required, Validators.min(1)]],
    codAmount: [0, [Validators.min(0)]],
    note: [''],
  });

  constructor() {
    // Reset to a blank form and (re)load the city list fresh every time the modal opens.
    effect(() => {
      if (!this.open()) {
        return;
      }
      this.actionError.set(null);
      this.districts.set([]);
      this.wards.set([]);
      this.clearQuote();
      this.form.reset({
        toName: '',
        toPhone: '',
        toAddress: '',
        toCityId: '',
        toDistrictId: '',
        toWardId: '',
        weightGrams: 500,
        codAmount: 0,
        note: '',
      });
      if (this.cities().length === 0) {
        this.loadCities();
      }
    });
  }

  private loadCities(): void {
    this.loadingCities.set(true);
    this.shipmentService.cities().subscribe({
      next: (res) => {
        this.loadingCities.set(false);
        this.cities.set(res.cities);
      },
      error: (err: HttpErrorResponse) => {
        this.loadingCities.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  onCityChange(event: Event): void {
    const cityId = (event.target as HTMLSelectElement).value;
    this.form.patchValue({ toDistrictId: '', toWardId: '' });
    this.districts.set([]);
    this.wards.set([]);
    this.clearQuote();
    if (!cityId) {
      return;
    }
    this.loadingDistricts.set(true);
    this.shipmentService.districts(cityId).subscribe({
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
    this.form.patchValue({ toWardId: '' });
    this.wards.set([]);
    this.clearQuote();
    if (!districtId) {
      return;
    }
    this.loadingWards.set(true);
    this.shipmentService.wards(districtId).subscribe({
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

  /** Weight and COD are priced on, so editing either makes the rates on screen wrong. */
  clearQuote(): void {
    this.rates.set([]);
    this.selectedRateId.set(null);
  }

  selectRate(rateId: string): void {
    this.selectedRateId.set(rateId);
  }

  quote(): void {
    const value = this.form.getRawValue();
    if (!value.toCityId || !value.toDistrictId || !value.weightGrams) {
      this.actionError.set({
        message: 'Chọn Tỉnh/Quận và nhập cân nặng trước khi xem giá.',
        isUpgradeRequired: false,
      });
      return;
    }
    this.quoting.set(true);
    this.actionError.set(null);
    this.shipmentService
      .rates({
        toCityId: value.toCityId,
        toDistrictId: value.toDistrictId,
        weightGrams: value.weightGrams,
        codAmount: value.codAmount,
      })
      .subscribe({
        next: (res) => {
          this.quoting.set(false);
          this.rates.set(res.rates);
          // Cheapest first from the backend, so preselecting the head is the
          // choice a cashier in a hurry would have made anyway.
          this.selectedRateId.set(res.rates.length > 0 ? res.rates[0].id : null);
        },
        error: (err: HttpErrorResponse) => {
          this.quoting.set(false);
          this.rates.set([]);
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
    const rateId = this.selectedRateId();
    const rate = this.rates().find((r) => r.id === rateId);
    if (!rate) {
      this.actionError.set({ message: 'Chọn hãng vận chuyển trước khi tạo đơn.', isUpgradeRequired: false });
      return;
    }

    const value = this.form.getRawValue();
    const city = this.cities().find((c) => c.id === value.toCityId);
    const district = this.districts().find((d) => d.id === value.toDistrictId);
    const ward = this.wards().find((w) => w.id === value.toWardId);
    if (!city || !district || !ward) {
      this.actionError.set({ message: 'Vui lòng chọn đầy đủ Tỉnh/Quận/Phường.', isUpgradeRequired: false });
      return;
    }

    this.submitting.set(true);
    this.actionError.set(null);
    const request: CreateShipmentRequest = {
      rateId: rate.id,
      toName: value.toName,
      toPhone: value.toPhone,
      toAddress: value.toAddress,
      toCityId: city.id,
      toCityName: city.name,
      toDistrictId: district.id,
      toDistrictName: district.name,
      toWardId: ward.id,
      toWardName: ward.name,
      weightGrams: value.weightGrams,
      codAmount: value.codAmount,
      note: value.note || undefined,
      service: rate.service,
      expected: rate.expected,
    };
    this.shipmentService.create(request).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.shipmentService.notifyChanged();
        this.saved.emit(res.shipment);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }
}
