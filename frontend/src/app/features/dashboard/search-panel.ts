import { Component, ElementRef, HostListener, computed, inject, input, output, signal } from '@angular/core';

export interface SearchField {
  key: string;
  placeholder: string;
}

export type SearchValues = Record<string, string>;

/**
 * KiotViet's search box: the box itself searches the document code, and the
 * sliders icon inside it opens a panel of the other things you might know
 * about a document - a product on it, the customer, the note.
 *
 * The box types live, the way the old single input did. The panel does not:
 * it edits a draft and applies on "Tìm kiếm", because a keystroke in "tên
 * hàng" would otherwise run a join across every line of every invoice.
 */
@Component({
  selector: 'app-search-panel',
  standalone: true,
  templateUrl: './search-panel.html',
})
export class SearchPanel {
  /** The first field is the one the box itself edits; the rest live in the panel. */
  readonly fields = input.required<SearchField[]>();
  readonly values = input.required<SearchValues>();

  readonly applied = output<SearchValues>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly open = signal(false);
  readonly draft = signal<SearchValues>({});

  readonly primaryField = computed(() => this.fields()[0]);
  readonly panelFields = computed(() => this.fields());

  /** Marks the icon when something other than the code box is narrowing the list. */
  readonly hasAdvanced = computed(() => {
    const primary = this.primaryField()?.key;
    return Object.entries(this.values()).some(([key, value]) => key !== primary && !!value);
  });

  draftValue(key: string): string {
    return this.draft()[key] ?? '';
  }

  onPrimaryInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.applied.emit({ ...this.values(), [this.primaryField().key]: value });
  }

  onDraftInput(key: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.draft.update((draft) => ({ ...draft, [key]: value }));
  }

  togglePanel(): void {
    if (!this.open()) {
      // Opens on what is actually filtering the list, not on whatever was left
      // in the boxes the last time the panel was closed without searching.
      this.draft.set({ ...this.values() });
    }
    this.open.update((open) => !open);
  }

  apply(): void {
    this.applied.emit({ ...this.draft() });
    this.open.set(false);
  }

  collapse(): void {
    this.open.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
