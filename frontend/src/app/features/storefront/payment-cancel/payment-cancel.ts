import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { readPendingOrder } from '../pending-order.storage';

@Component({
  selector: 'app-payment-cancel',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './payment-cancel.html',
})
export class PaymentCancel {
  // Deliberately not cleared here (unlike PaymentSuccess) so a retried
  // checkout naturally overwrites it instead of losing the store context.
  private readonly pending = readPendingOrder();

  readonly orderId = signal(this.pending?.orderId ?? null);
  readonly storeSlug = signal(this.pending?.storeSlug ?? null);
}
