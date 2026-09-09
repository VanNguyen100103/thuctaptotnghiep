import { Component, ElementRef, HostListener, inject, input, output, signal } from '@angular/core';

export interface ColumnDef {
  key: string;
  label: string;
}

/**
 * KiotViet's column chooser - the list icon sitting in the toolbar above every
 * one of its tables. Which columns a shop cares about differs by shop, and the
 * choice is per browser, so the parent persists it in localStorage.
 */
@Component({
  selector: 'app-column-picker',
  standalone: true,
  templateUrl: './column-picker.html',
})
export class ColumnPicker {
  readonly columns = input.required<ColumnDef[]>();
  readonly visible = input.required<string[]>();

  /** Emits the whole new set of visible keys. */
  readonly changed = output<string[]>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly open = signal(false);

  isVisible(key: string): boolean {
    return this.visible().includes(key);
  }

  toggleOpen(): void {
    this.open.update((open) => !open);
  }

  /**
   * Hiding every column would leave an empty grid with no way back, so the
   * last visible one refuses to switch off - the same floor KiotViet keeps.
   */
  toggle(key: string): void {
    const current = this.visible();
    if (current.includes(key)) {
      if (current.length === 1) {
        return;
      }
      this.changed.emit(current.filter((k) => k !== key));
      return;
    }
    // Re-derived from the column order so a re-checked column returns to its own place.
    this.changed.emit(this.columns().map((c) => c.key).filter((k) => k === key || current.includes(k)));
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
