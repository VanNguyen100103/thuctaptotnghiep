import { Component, ElementRef, HostListener, computed, inject, input, output, signal } from '@angular/core';

export interface SelectOption {
  value: string;
  label: string;
}

/**
 * The single-choice half of KiotViet's filter card. A native <select> is the
 * one control on these screens the browser draws itself - its arrow, its font
 * and its popup all come from the OS, which is exactly what made the sidebar
 * read as a different product next to theirs. This draws the whole thing.
 */
@Component({
  selector: 'app-filter-select',
  standalone: true,
  templateUrl: './filter-select.html',
})
export class FilterSelect {
  readonly options = input.required<SelectOption[]>();
  readonly value = input.required<string>();
  readonly placeholder = input('Chọn giá trị');

  readonly changed = output<string>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly open = signal(false);

  readonly selectedLabel = computed(
    () => this.options().find((option) => option.value === this.value())?.label ?? null,
  );

  toggleOpen(): void {
    this.open.update((open) => !open);
  }

  select(value: string): void {
    this.open.set(false);
    this.changed.emit(value);
  }

  /**
   * Closes on a click anywhere outside this control. The check is "is the
   * click inside my own host", not stopPropagation on the panel: several of
   * these sit in one sidebar, and a control that swallows its own clicks
   * never lets its neighbours hear about them - so opening one would leave
   * the previous one hanging open over it.
   */
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
