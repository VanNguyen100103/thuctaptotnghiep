import { Component, computed, input, model, output, signal } from '@angular/core';

import { ChipInput } from './chip-input';
import { UnitFormModal } from './unit-form-modal';
import {
  ATTRIBUTE_NAME_PRESETS,
  ATTRIBUTE_VALUE_PRESETS,
  AttributeGroup,
  MAX_ATTRIBUTE_GROUPS,
  UNIT_AXIS_NAME,
  UnitDef,
  VariantRowDraft,
} from './variant-builder.models';

const CUSTOM_NAME_OPTION = '__custom__';

/**
 * KiotViet's "Thiết lập đơn vị tính và thuộc tính" popup - opened from the
 * collapsed summary row in product-form.html. `units`/`attributeGroups` are
 * two-way (model()) bound straight to ProductForm's own signals, so there's
 * a single source of truth; row generation itself stays in ProductForm
 * (generate output) rather than being duplicated here.
 */
@Component({
  selector: 'app-unit-attribute-setup',
  standalone: true,
  imports: [ChipInput, UnitFormModal],
  templateUrl: './unit-attribute-setup.html',
})
export class UnitAttributeSetup {
  readonly units = model.required<UnitDef[]>();
  readonly attributeGroups = model.required<AttributeGroup[]>();
  readonly variantRows = input.required<VariantRowDraft[]>();
  readonly variantColumnNames = input.required<string[]>();
  readonly basePrice = input(0);

  readonly generate = output<void>();
  readonly updateVariantRow = output<{ index: number; patch: Partial<VariantRowDraft> }>();
  readonly removeVariantRow = output<number>();
  readonly closed = output<void>();

  readonly attributeNamePresets = ATTRIBUTE_NAME_PRESETS;
  readonly customNameOption = CUSTOM_NAME_OPTION;

  readonly unitFormOpen = signal(false);
  readonly unitFormIsBase = computed(() => this.units().length === 0);
  readonly baseUnitName = computed(() => this.units()[0]?.name ?? '');
  readonly baseUnitPrice = computed(() => this.units()[0]?.price ?? this.basePrice());

  /** Group indexes currently showing a free-text input instead of the preset dropdown (picked "+ Tạo thuộc tính mới", or loaded with a non-preset name). */
  readonly customNameEditing = signal<Set<number>>(new Set());
  readonly quickPickOpenIndex = signal<number | null>(null);

  readonly totalAxisCount = computed(() => this.attributeGroups().length + (this.units().length > 0 ? 1 : 0));
  readonly canAddAttributeGroup = computed(() => this.totalAxisCount() < MAX_ATTRIBUTE_GROUPS);

  readonly hasUnitColumn = computed(() => this.variantColumnNames().includes(UNIT_AXIS_NAME));
  readonly hasAttributeColumn = computed(() => this.variantColumnNames().some((c) => c !== UNIT_AXIS_NAME));

  quickPickValues(name: string): string[] {
    return ATTRIBUTE_VALUE_PRESETS[name] ?? [];
  }

  isCustomNameEditing(index: number): boolean {
    const group = this.attributeGroups()[index];
    return this.customNameEditing().has(index) || (!!group?.name && !this.attributeNamePresets.includes(group.name));
  }

  openAddUnit(): void {
    this.unitFormOpen.set(true);
  }

  onUnitSaved(event: { unit: UnitDef; addAnother: boolean }): void {
    this.units.update((list) => [...list, event.unit]);
    if (!event.addAnother) {
      this.unitFormOpen.set(false);
    }
    this.generate.emit();
  }

  onUnitDismissed(): void {
    this.unitFormOpen.set(false);
  }

  removeUnitAt(index: number): void {
    this.units.update((list) => list.filter((_, i) => i !== index));
    this.generate.emit();
  }

  addAttributeGroup(): void {
    if (!this.canAddAttributeGroup()) {
      return;
    }
    this.attributeGroups.update((groups) => [...groups, { name: '', values: [] }]);
  }

  removeAttributeGroupAt(index: number): void {
    this.attributeGroups.update((groups) => groups.filter((_, i) => i !== index));
    this.customNameEditing.update((set) => {
      const next = new Set([...set].filter((i) => i !== index).map((i) => (i > index ? i - 1 : i)));
      return next;
    });
    this.generate.emit();
  }

  selectAttributeName(index: number, value: string): void {
    if (value === CUSTOM_NAME_OPTION) {
      this.customNameEditing.update((set) => new Set(set).add(index));
      this.renameAttributeGroup(index, '');
      return;
    }
    this.renameAttributeGroup(index, value);
  }

  renameAttributeGroup(index: number, name: string): void {
    this.attributeGroups.update((groups) => groups.map((g, i) => (i === index ? { ...g, name } : g)));
    this.generate.emit();
  }

  updateAttributeGroupValues(index: number, values: string[]): void {
    this.attributeGroups.update((groups) => groups.map((g, i) => (i === index ? { ...g, values } : g)));
    this.generate.emit();
  }

  toggleQuickPick(index: number): void {
    this.quickPickOpenIndex.update((current) => (current === index ? null : index));
  }

  applyQuickPickValue(index: number, value: string): void {
    const group = this.attributeGroups()[index];
    if (!group || group.values.some((v) => v.toLowerCase() === value.toLowerCase())) {
      return;
    }
    this.updateAttributeGroupValues(index, [...group.values, value]);
  }

  /** Non-unit attribute values for one generated row, joined for the combined "Giá trị thuộc tính" column. */
  attributeValueLabel(row: VariantRowDraft): string {
    return this.variantColumnNames()
      .filter((c) => c !== UNIT_AXIS_NAME)
      .map((c) => row.attributeValues[c])
      .join(', ');
  }

  unitValueOf(row: VariantRowDraft): string | undefined {
    return row.attributeValues[UNIT_AXIS_NAME];
  }

  conversionFactorOf(row: VariantRowDraft): number {
    return this.units().find((u) => u.name === this.unitValueOf(row))?.conversionFactor ?? 1;
  }

  patchRow(index: number, patch: Partial<VariantRowDraft>): void {
    this.updateVariantRow.emit({ index, patch });
  }

  deleteRow(index: number): void {
    this.removeVariantRow.emit(index);
  }

  close(): void {
    this.closed.emit();
  }
}
