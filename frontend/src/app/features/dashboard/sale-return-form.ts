import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { ActionErrorBanner } from './action-error-banner';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { SALE_PAYMENT_METHOD_LABELS, SalePaymentMethod } from './sale.models';
import {
  REFUND_METHOD_OPTIONS,
  ReturnableLineDTO,
  ReturnableSaleDTO,
  proratedInvoiceDiscount,
  proratedPoints,
  returnedLineTotal,
} from './sale-return.models';
import { SaleReturnService } from './sale-return.service';
import { ActionError, toActionError } from './subscription-error.util';

/**
 * "Trả hàng" - the form that takes goods back off one invoice. Always opens
 * on an invoice (`?saleId=`), reached from the "Trả hàng" button on the Hóa
 * đơn detail panel: a refund with no invoice behind it is money leaving the
 * till that nothing accounts for, which is why the backend requires one too.
 *
 * Every line opens at its full remaining quantity, the way KiotViet's own
 * return screen does - the customer handing back the whole invoice is the
 * common case, and each line can be dialled down from there.
 *
 * The money shown here is a preview computed with the same rules as
 * SaleReturnService (see sale-return.models). The server recomputes all of it
 * from the invoice on submit and stays the authority - nothing priced on this
 * screen is sent.
 */
@Component({
  selector: 'app-sale-return-form',
  standalone: true,
  imports: [RouterLink, DatePipe, VndCurrencyPipe, ActionErrorBanner],
  templateUrl: './sale-return-form.html',
})
export class SaleReturnForm {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly saleReturnService = inject(SaleReturnService);

  readonly paymentMethodLabels = SALE_PAYMENT_METHOD_LABELS;
  readonly refundMethodOptions = REFUND_METHOD_OPTIONS;

  private readonly queryParams = toSignal(this.route.queryParamMap, { requireSync: true });

  readonly saleId = computed(() => {
    const raw = this.queryParams()!.get('saleId');
    return raw ? Number(raw) : null;
  });

  readonly saleState = toSignal(
    toObservable(this.saleId).pipe(
      switchMap((id) =>
        id === null
          ? [INITIAL_API_STATE]
          : toApiState<ReturnableSaleDTO>(this.saleReturnService.returnable(id)),
      ),
    ),
    { initialValue: INITIAL_API_STATE },
  );

  readonly sale = computed(() => this.saleState().data);

  /**
   * How many units of each line are coming back, keyed by invoice line. Left
   * unset until the invoice arrives, at which point every line reads as its
   * full remaining quantity - see quantityOf().
   */
  private readonly quantityOverrides = signal<Record<number, number>>({});

  quantityOf(line: ReturnableLineDTO): number {
    const override = this.quantityOverrides()[line.saleItemId];
    return override === undefined ? line.returnableQuantity : override;
  }

  onQuantityChange(line: ReturnableLineDTO, event: Event): void {
    const raw = Number((event.target as HTMLInputElement).value);
    const clamped = Math.max(0, Math.min(Math.floor(raw) || 0, line.returnableQuantity));
    this.quantityOverrides.update((current) => ({ ...current, [line.saleItemId]: clamped }));
  }

  lineTotalOf(line: ReturnableLineDTO): number {
    return returnedLineTotal(line, this.quantityOf(line));
  }

  /** Only the lines with something on them - what actually gets sent. */
  private readonly returningLines = computed(() =>
    (this.sale()?.lines ?? []).filter((line) => this.quantityOf(line) > 0),
  );

  readonly totalQuantity = computed(() =>
    this.returningLines().reduce((sum, line) => sum + this.quantityOf(line), 0),
  );

  readonly totalGoodsValue = computed(() =>
    this.returningLines().reduce((sum, line) => sum + this.lineTotalOf(line), 0),
  );

  /** "Giảm giá phân bổ" - the returned goods' share of what came off the whole invoice. */
  readonly proratedDiscount = computed(() => {
    const sale = this.sale();
    return sale ? proratedInvoiceDiscount(sale, this.totalGoodsValue()) : 0;
  });

  readonly returnFee = signal(0);

  onReturnFeeChange(event: Event): void {
    this.returnFee.set(Math.max(0, Number((event.target as HTMLInputElement).value) || 0));
  }

  /** "Cần trả khách" = tiền hàng trả lại - giảm giá phân bổ - phí trả hàng. */
  readonly refundAmount = computed(() =>
    Math.max(0, this.totalGoodsValue() - this.proratedDiscount() - this.returnFee()),
  );

  /** The fee cannot eat more than the goods are worth - the backend rejects that outright. */
  readonly returnFeeTooHigh = computed(
    () => this.returnFee() > this.totalGoodsValue() - this.proratedDiscount(),
  );

  readonly pointsRestored = computed(() => {
    const sale = this.sale();
    return sale
      ? proratedPoints(sale.pointsRedeemed, sale.pointsAlreadyRestored, sale.subtotal, this.totalGoodsValue())
      : 0;
  });

  readonly pointsReverted = computed(() => {
    const sale = this.sale();
    return sale
      ? proratedPoints(sale.pointsEarned, sale.pointsAlreadyReverted, sale.subtotal, this.totalGoodsValue())
      : 0;
  });

  readonly refundMethod = signal<SalePaymentMethod>('CASH');

  /**
   * Defaults to the tender that brought the most in, so the money usually
   * goes back the way it came. A signal rather than a computed because the
   * cashier can override it - refunding a card sale in cash is allowed.
   */
  private readonly defaultRefundMethodApplied = signal(false);

  readonly suggestedRefundMethod = computed<SalePaymentMethod | null>(() => {
    const payments = this.sale()?.payments ?? [];
    if (payments.length === 0) {
      return null;
    }
    return payments.reduce((biggest, tender) => (tender.amount > biggest.amount ? tender : biggest)).method;
  });

  constructor() {
    // Applied once, when the invoice lands - after that the select is the
    // cashier's to change and must not snap back.
    effect(() => {
      const method = this.suggestedRefundMethod();
      if (method !== null && !this.defaultRefundMethodApplied()) {
        this.refundMethod.set(method);
        this.defaultRefundMethodApplied.set(true);
      }
    });
  }

  onRefundMethodChange(event: Event): void {
    this.refundMethod.set((event.target as HTMLSelectElement).value as SalePaymentMethod);
  }

  /**
   * A bank-transfer refund is the one that cannot be automated here: SePay
   * only watches the account for incoming and outgoing transfers, it has no
   * API to send money (see SePayPaymentProvider#refund). The shop owner makes
   * the transfer in their own banking app; this document is what records it.
   */
  readonly isBankTransferRefund = computed(() => this.refundMethod() === 'BANK_TRANSFER');

  readonly note = signal('');

  onNoteChange(event: Event): void {
    this.note.set((event.target as HTMLTextAreaElement).value);
  }

  readonly submitting = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  readonly canSubmit = computed(
    () => this.returningLines().length > 0 && !this.returnFeeTooHigh() && !this.submitting(),
  );

  submit(): void {
    const sale = this.sale();
    if (!sale || !this.canSubmit()) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.saleReturnService
      .create({
        saleId: sale.saleId,
        returnFee: this.returnFee(),
        refundMethod: this.refundMethod(),
        note: this.note().trim() || null,
        items: this.returningLines().map((line) => ({
          saleItemId: line.saleItemId,
          quantity: this.quantityOf(line),
        })),
      })
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          // Lands on the list with the new document open, so the cashier sees
          // the refund that was just recorded rather than an empty form.
          this.router.navigate(['/dashboard/sale-returns'], {
            queryParams: { selected: response.saleReturn.id },
          });
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.actionError.set(toActionError(err));
        },
      });
  }
}
