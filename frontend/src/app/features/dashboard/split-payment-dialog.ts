import { Component, computed, effect, inject, input, output, signal } from '@angular/core';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { SALE_PAYMENT_METHOD_LABELS, SalePaymentMethod } from './sale.models';

export interface SplitPaymentLine {
  method: SalePaymentMethod;
  amount: number;
}

/** Round `amount` up to the next multiple of `unit` (or `amount` itself if already a multiple). */
function roundUpTo(amount: number, unit: number): number {
  return Math.ceil(amount / unit) * unit;
}

/**
 * "Thanh toán nhiều phương thức" - the split-tender dialog opened from the
 * "⋯" button next to the payment method radios on the Bán hàng checkout
 * panel. Each click on a method button commits whatever is in "Số tiền" as
 * one tender line (e.g. part cash, part bank transfer) - matches the real
 * KiotViet dialog exactly (user-supplied screenshots): quick round-up
 * amount suggestions, a running list of added lines with a delete icon,
 * and "Xong" only enabled once the lines fully cover what's owed.
 */
@Component({
  selector: 'app-split-payment-dialog',
  standalone: true,
  imports: [VndCurrencyPipe],
  templateUrl: './split-payment-dialog.html',
})
export class SplitPaymentDialog {
  readonly open = input.required<boolean>();
  readonly totalDue = input.required<number>();
  /** Carries over lines already chosen if the dialog is reopened mid-checkout. */
  readonly initialLines = input<SplitPaymentLine[]>([]);

  readonly confirmed = output<SplitPaymentLine[]>();
  readonly closed = output<void>();

  readonly methods: SalePaymentMethod[] = ['CASH', 'BANK_TRANSFER', 'CARD', 'EWALLET'];
  readonly methodLabels = SALE_PAYMENT_METHOD_LABELS;

  readonly lines = signal<SplitPaymentLine[]>([]);
  readonly amountInput = signal(0);

  readonly sumLines = computed(() => this.lines().reduce((sum, l) => sum + l.amount, 0));
  readonly remaining = computed(() => Math.max(0, this.totalDue() - this.sumLines()));
  readonly fullyPaid = computed(() => this.sumLines() >= this.totalDue() && this.totalDue() > 0);

  /** Exact remaining amount + round-ups to the next 50k/100k/200k/500k VND note, deduped - reproduces KiotViet's own suggested-tender buttons. */
  readonly quickAmounts = computed(() => {
    const remaining = this.remaining();
    if (remaining <= 0) {
      return [0];
    }
    const candidates = [remaining, roundUpTo(remaining, 50_000), roundUpTo(remaining, 100_000), roundUpTo(remaining, 200_000), roundUpTo(remaining, 500_000)];
    return Array.from(new Set(candidates)).sort((a, b) => a - b);
  });

  /** Not a signal on purpose - see the comment below on why this effect must not track initialLines()/totalDue() as ongoing dependencies. */
  private wasOpen = false;

  constructor() {
    // Only re-seeds state on the *closed -> open* transition, not on every
    // change-detection tick the dialog happens to stay open for. The parent
    // passes `[initialLines]="splitLines() ?? []"`, which builds a fresh `[]`
    // reference on every CD pass while no split is confirmed yet - reading
    // initialLines()/totalDue() unconditionally here would make the effect
    // re-run on every keystroke in the amount field and wipe out whatever
    // the cashier just typed/added. Guarding the read behind the rising edge
    // means those two signals are only tracked as dependencies on the run
    // where they're actually read (Angular's effect() dependency tracking is
    // per-run, not static), so later reference churn while open is ignored.
    effect(() => {
      const isOpen = this.open();
      if (isOpen && !this.wasOpen) {
        this.lines.set(this.initialLines());
        const paidSoFar = this.initialLines().reduce((sum, l) => sum + l.amount, 0);
        this.amountInput.set(Math.max(0, this.totalDue() - paidSoFar));
      }
      this.wasOpen = isOpen;
    });
  }

  setAmount(value: number): void {
    this.amountInput.set(Math.max(0, value));
  }

  onAmountInput(event: Event): void {
    this.setAmount(Number((event.target as HTMLInputElement).value) || 0);
  }

  /** Commits the current "Số tiền" as one tender line, then resets the field to whatever is still owed. */
  addLine(method: SalePaymentMethod): void {
    if (this.amountInput() <= 0) {
      return;
    }
    this.lines.update((rows) => [...rows, { method, amount: this.amountInput() }]);
    this.amountInput.set(this.remaining());
  }

  removeLine(index: number): void {
    this.lines.update((rows) => rows.filter((_, i) => i !== index));
    this.amountInput.set(this.remaining());
  }

  dismiss(): void {
    this.closed.emit();
  }

  confirm(): void {
    if (!this.fullyPaid()) {
      return;
    }
    this.confirmed.emit(this.lines());
  }
}
