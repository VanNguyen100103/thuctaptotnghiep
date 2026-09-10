import { Component, input, output, signal } from '@angular/core';

import { SALES_CHANNELS, SALES_CHANNEL_LABELS, SalesChannel } from './order.models';

/** What the form is asking the backend to change; an absent key means "leave this one alone". */
export interface OrderBulkEdit {
  recipientName?: string | null;
  salesChannel?: string | null;
  notes?: string | null;
}

/**
 * "Sửa người nhận đặt, kênh bán, ghi chú" - the bulk edit behind the "..."
 * button, applied to every ticked order at once.
 *
 * Each field has its own tick box, because leaving a box empty is ambiguous
 * on a form that edits many rows: it could mean "don't touch this" or "clear
 * it everywhere". The tick answers that, and only ticked fields are sent -
 * so an empty ticked box does clear the field, deliberately.
 */
@Component({
  selector: 'app-order-bulk-edit-modal',
  standalone: true,
  templateUrl: './order-bulk-edit-modal.html',
})
export class OrderBulkEditModal {
  readonly open = input.required<boolean>();
  /** How many orders the change lands on - the shop should see the blast radius before it presses save. */
  readonly count = input.required<number>();
  readonly saving = input(false);

  readonly applied = output<OrderBulkEdit>();
  readonly closed = output<void>();

  readonly channelOptions = SALES_CHANNELS.map((channel) => ({
    value: channel,
    label: SALES_CHANNEL_LABELS[channel],
  }));

  readonly editRecipient = signal(false);
  readonly editChannel = signal(false);
  readonly editNotes = signal(false);

  readonly recipientName = signal('');
  readonly salesChannel = signal<SalesChannel>('DIRECT');
  readonly notes = signal('');

  onRecipientInput(event: Event): void {
    this.recipientName.set((event.target as HTMLInputElement).value);
    this.editRecipient.set(true);
  }

  onChannelChange(event: Event): void {
    this.salesChannel.set((event.target as HTMLSelectElement).value as SalesChannel);
    this.editChannel.set(true);
  }

  onNotesInput(event: Event): void {
    this.notes.set((event.target as HTMLTextAreaElement).value);
    this.editNotes.set(true);
  }

  hasChange(): boolean {
    return this.editRecipient() || this.editChannel() || this.editNotes();
  }

  submit(): void {
    if (!this.hasChange()) {
      return;
    }
    const change: OrderBulkEdit = {};
    if (this.editRecipient()) {
      change.recipientName = this.recipientName().trim();
    }
    if (this.editChannel()) {
      change.salesChannel = this.salesChannel();
    }
    if (this.editNotes()) {
      change.notes = this.notes().trim();
    }
    this.applied.emit(change);
  }

  /** Reopening on last time's half-filled form would apply changes nobody meant this time. */
  reset(): void {
    this.editRecipient.set(false);
    this.editChannel.set(false);
    this.editNotes.set(false);
    this.recipientName.set('');
    this.salesChannel.set('DIRECT');
    this.notes.set('');
  }

  cancel(): void {
    this.reset();
    this.closed.emit();
  }
}
