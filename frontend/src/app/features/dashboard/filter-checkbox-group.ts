import { Component, input, output } from '@angular/core';

import { FilterOption } from './filter-multiselect';

/**
 * KiotViet's other filter shape: a short list of ticked boxes standing open
 * in the sidebar rather than folded into a dropdown. It is what they use for
 * the two or three facets a shop reads at a glance - "Loại hóa đơn",
 * "Trạng thái hóa đơn" - where the whole point is seeing which states are on
 * without opening anything. Longer lists stay in FilterMultiselect, which
 * costs one line however many options it holds.
 */
@Component({
  selector: 'app-filter-checkbox-group',
  standalone: true,
  templateUrl: './filter-checkbox-group.html',
})
export class FilterCheckboxGroup {
  readonly options = input.required<FilterOption[]>();
  readonly selected = input.required<string[]>();

  /** Emits the whole new selection, not the toggled value - the parent keeps one signal and sets it. */
  readonly changed = output<string[]>();

  isChecked(value: string): boolean {
    return this.selected().includes(value);
  }

  toggle(value: string): void {
    const current = this.selected();
    this.changed.emit(
      current.includes(value) ? current.filter((v) => v !== value) : [...current, value],
    );
  }
}
