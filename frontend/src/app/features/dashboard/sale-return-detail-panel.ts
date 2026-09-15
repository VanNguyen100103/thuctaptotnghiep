import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { ActionErrorBanner } from './action-error-banner';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { SALE_PAYMENT_METHOD_LABELS } from './sale.models';
import { SALE_RETURN_REFUND_STATUS_LABELS, SaleReturnDTO } from './sale-return.models';
import { SaleReturnService } from './sale-return.service';
import { ActionError, toActionError } from './subscription-error.util';

/**
 * The panel that opens inside the "Trả hàng" list when a row is clicked. The
 * receipt itself only reads - a return is final the moment the goods come
 * back over the counter (see backend SaleReturn).
 *
 * The refund leg is the exception, and the reason this panel has a button at
 * all. A transfer refund leaves the till in the owner's banking app, not
 * here, so until that happens the panel has to say so plainly and offer the
 * transfer content to type - showing "Chuyển khoản 86.000đ" and nothing else
 * reads as though the app already paid.
 */
@Component({
  selector: 'app-sale-return-detail-panel',
  standalone: true,
  imports: [RouterLink, DatePipe, VndCurrencyPipe, ActionErrorBanner],
  templateUrl: './sale-return-detail-panel.html',
})
export class SaleReturnDetailPanel {
  private readonly saleReturnService = inject(SaleReturnService);

  readonly saleReturnId = input.required<number>();
  readonly closed = output<void>();

  /** Raised after a refund is ticked off, so the list can refresh its "còn nợ khách" total. */
  readonly refundSettled = output<void>();

  readonly paymentMethodLabels = SALE_PAYMENT_METHOD_LABELS;
  readonly refundStatusLabels = SALE_RETURN_REFUND_STATUS_LABELS;

  private readonly fetched = toSignal(
    toObservable(this.saleReturnId).pipe(
      switchMap((id) => toApiState<SaleReturnDTO>(this.saleReturnService.getById(id))),
    ),
    { initialValue: INITIAL_API_STATE },
  );

  /** Set by marking the refund paid, so the panel updates without another round trip. */
  private readonly settled = signal<SaleReturnDTO | null>(null);

  readonly detailState = computed(() => {
    const settled = this.settled();
    const state = this.fetched();
    // Only ever replaces the receipt it was fetched for - switching rows
    // clears it via the id check rather than showing the previous one.
    return settled && settled.id === this.saleReturnId() ? { data: settled, error: null } : state;
  });

  readonly awaitingTransfer = computed(() => this.detailState().data?.refundStatus === 'PENDING');

  readonly marking = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  markRefunded(): void {
    const saleReturn = this.detailState().data;
    if (!saleReturn || this.marking()) {
      return;
    }
    this.marking.set(true);
    this.actionError.set(null);
    this.saleReturnService.markRefunded(saleReturn.id).subscribe({
      next: (response) => {
        this.marking.set(false);
        this.settled.set(response.saleReturn);
        this.refundSettled.emit();
      },
      error: (err: HttpErrorResponse) => {
        this.marking.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  readonly copied = signal(false);

  /** The transfer content is the whole point of showing it - make it one click to carry into the banking app. */
  copyTransferContent(content: string): void {
    navigator.clipboard?.writeText(content).then(
      () => {
        this.copied.set(true);
        setTimeout(() => this.copied.set(false), 2000);
      },
      () => this.copied.set(false),
    );
  }
}
