import { Component, ElementRef, HostListener, inject, input, output, signal } from '@angular/core';

export interface ActionMenuItem {
  key: string;
  label: string;
  /** Drawn in red, the way KiotViet colours the actions that end a document. */
  danger?: boolean;
  disabled?: boolean;
  /** Why it is disabled - shown as the row's tooltip, since a greyed row with no explanation just reads as broken. */
  disabledReason?: string;
  /** A rule above this row, separating a group of actions from the one before it. */
  separatorBefore?: boolean;
}

/**
 * The drop-down behind a toolbar button - KiotViet's "Xuất file ▾" and the
 * "..." beside it are the same control with different contents, so they are
 * one component here.
 *
 * A trigger with a `label` draws it with a chevron; one without draws the
 * three-dot glyph, which is how the "..." button appears on every one of
 * KiotViet's list screens.
 */
@Component({
  selector: 'app-action-menu',
  standalone: true,
  templateUrl: './action-menu.html',
})
export class ActionMenu {
  readonly items = input.required<ActionMenuItem[]>();
  /** Null draws the three-dot trigger instead of a labelled button. */
  readonly label = input<string | null>(null);
  readonly disabled = input(false);
  readonly ariaLabel = input('Thêm thao tác');
  /** Menus at the right edge of the toolbar open leftwards so they stay on screen. */
  readonly align = input<'left' | 'right'>('right');

  readonly picked = output<string>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly open = signal(false);

  toggleOpen(): void {
    if (this.disabled()) {
      return;
    }
    this.open.update((open) => !open);
  }

  select(item: ActionMenuItem): void {
    if (item.disabled) {
      return;
    }
    this.open.set(false);
    this.picked.emit(item.key);
  }

  /** Same outside-click rule as FilterMultiselect - see the note there for why it is not stopPropagation. */
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
