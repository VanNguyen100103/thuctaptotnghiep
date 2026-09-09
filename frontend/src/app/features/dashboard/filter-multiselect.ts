import { Component, HostListener, computed, input, output, signal } from '@angular/core';

export interface FilterOption {
  value: string;
  label: string;
}

/**
 * KiotViet's filter control: a bordered box holding the picked values as
 * removable chips ("Phiếu tạm ×", "Đang giao hàng ×", "+1 khác"), opening a
 * checkbox list on click. It replaces a bare column of checkboxes, which
 * takes the whole sidebar to say what three chips say.
 *
 * Only the first few chips are drawn - past that the box would grow tall
 * enough to push the rest of the sidebar off screen, so the remainder is
 * counted instead, exactly as KiotViet does it.
 */
@Component({
  selector: 'app-filter-multiselect',
  standalone: true,
  templateUrl: './filter-multiselect.html',
})
export class FilterMultiselect {
  readonly options = input.required<FilterOption[]>();
  readonly selected = input.required<string[]>();
  readonly placeholder = input('Chọn giá trị');
  readonly maxChips = input(3);

  /** Emits the whole new selection, not the toggled value - the parent keeps one signal and sets it. */
  readonly changed = output<string[]>();

  readonly open = signal(false);

  private readonly selectedOptions = computed(() =>
    this.options().filter((option) => this.selected().includes(option.value)),
  );

  readonly visibleChips = computed(() => this.selectedOptions().slice(0, this.maxChips()));

  readonly hiddenCount = computed(() => Math.max(this.selectedOptions().length - this.maxChips(), 0));

  isChecked(value: string): boolean {
    return this.selected().includes(value);
  }

  toggleOpen(): void {
    this.open.update((open) => !open);
  }

  toggle(value: string): void {
    const current = this.selected();
    this.changed.emit(current.includes(value) ? current.filter((v) => v !== value) : [...current, value]);
  }

  remove(value: string): void {
    this.changed.emit(this.selected().filter((v) => v !== value));
  }

  selectAll(): void {
    this.changed.emit(this.options().map((option) => option.value));
  }

  clear(): void {
    this.changed.emit([]);
  }

  /** Closes on any outside click; the box itself stops propagation so clicks inside never reach here. */
  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.open()) {
      this.open.set(false);
    }
  }
}
