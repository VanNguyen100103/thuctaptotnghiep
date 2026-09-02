import { Component, input, model, signal } from '@angular/core';

@Component({
  selector: 'app-chip-input',
  standalone: true,
  templateUrl: './chip-input.html',
})
export class ChipInput {
  readonly label = input.required<string>();
  readonly placeholder = input('Nhấn Enter để thêm');
  readonly values = model<string[]>([]);

  readonly draft = signal('');

  addFromInput(): void {
    const value = this.draft().trim();
    if (!value) {
      return;
    }
    const exists = this.values().some((v) => v.toLowerCase() === value.toLowerCase());
    if (!exists) {
      this.values.update((values) => [...values, value]);
    }
    this.draft.set('');
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.addFromInput();
    }
  }

  remove(value: string): void {
    this.values.update((values) => values.filter((v) => v !== value));
  }
}
