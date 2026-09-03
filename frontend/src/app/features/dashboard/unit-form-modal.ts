import { Component, computed, effect, input, output, signal } from '@angular/core';

import { UnitDef } from './variant-builder.models';

/**
 * KiotViet's "Thêm đơn vị cơ bản" / "Thêm đơn vị" popup, nested inside
 * UnitAttributeSetup. One component for both: `isBaseUnit` toggles whether
 * "Giá trị quy đổi" shows. The reference unit for a derived unit's ratio is
 * always the base unit (not an arbitrary picker) - the reference video never
 * exercises chaining a unit off another derived unit, so resolving arbitrary
 * conversion chains isn't built.
 */
@Component({
  selector: 'app-unit-form-modal',
  standalone: true,
  templateUrl: './unit-form-modal.html',
})
export class UnitFormModal {
  readonly isBaseUnit = input(false);
  readonly baseUnitName = input('');
  readonly basePrice = input(0);

  readonly saved = output<{ unit: UnitDef; addAnother: boolean }>();
  readonly dismissed = output<void>();

  readonly name = signal('');
  readonly conversionFactor = signal(1);
  readonly price = signal(0);
  readonly sellDirectly = signal(true);
  readonly error = signal<string | null>(null);

  private priceTouched = false;

  readonly suggestedPrice = computed(() => Math.round(this.conversionFactor() * this.basePrice()));

  constructor() {
    // Base unit's price defaults to (and tracks) the outer form's own Giá bán
    // field, matching the reference's pre-filled 7,000. Derived units get
    // the same treatment once a conversion factor is entered (see below).
    effect(() => {
      if (this.isBaseUnit() && !this.priceTouched) {
        this.price.set(this.basePrice());
      }
    });
  }

  onConversionFactorInput(value: number): void {
    this.conversionFactor.set(value);
    if (!this.priceTouched) {
      this.price.set(this.suggestedPrice());
    }
  }

  onPriceInput(value: number): void {
    this.priceTouched = true;
    this.price.set(value);
  }

  private validate(): UnitDef | null {
    const trimmedName = this.name().trim();
    if (!trimmedName) {
      this.error.set(this.isBaseUnit() ? 'Tên đơn vị cơ bản bắt buộc.' : 'Tên đơn vị bắt buộc.');
      return null;
    }
    if (!this.isBaseUnit() && this.conversionFactor() < 1) {
      this.error.set('Giá trị quy đổi phải lớn hơn hoặc bằng 1.');
      return null;
    }
    if (this.price() <= 0) {
      this.error.set('Giá bán phải lớn hơn 0.');
      return null;
    }
    this.error.set(null);
    return {
      name: trimmedName,
      conversionFactor: this.isBaseUnit() ? 1 : this.conversionFactor(),
      price: this.price(),
      sellDirectly: this.sellDirectly(),
    };
  }

  private reset(): void {
    this.name.set('');
    this.conversionFactor.set(1);
    this.price.set(0);
    this.sellDirectly.set(true);
    this.priceTouched = false;
  }

  save(addAnother: boolean): void {
    const unit = this.validate();
    if (!unit) {
      return;
    }
    this.saved.emit({ unit, addAnother });
    if (addAnother) {
      this.reset();
    }
  }

  dismiss(): void {
    this.dismissed.emit();
  }
}
