import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { SALE_PAYMENT_METHOD_LABELS, SaleDTO } from './sale.models';
import { SaleService } from './sale.service';

/**
 * The panel that opens inside the "Hóa đơn" list when a row is clicked. A POS
 * sale is final the moment it is paid (see SaleService) - there is no status
 * to move it through, so unlike the Đặt hàng panel this one only reads.
 */
@Component({
  selector: 'app-invoice-detail-panel',
  standalone: true,
  imports: [DatePipe, VndCurrencyPipe],
  templateUrl: './invoice-detail-panel.html',
})
export class InvoiceDetailPanel {
  private readonly saleService = inject(SaleService);

  readonly saleId = input.required<number>();
  readonly closed = output<void>();

  readonly paymentMethodLabels = SALE_PAYMENT_METHOD_LABELS;

  readonly detailState = toSignal(
    toObservable(this.saleId).pipe(switchMap((id) => toApiState<SaleDTO>(this.saleService.getById(id)))),
    { initialValue: INITIAL_API_STATE },
  );

  /** The two discounts are stored apart (invoice-level vs coupon vs points), so the panel adds them up the way the receipt does. */
  readonly totalDiscount = computed(() => {
    const sale = this.detailState().data;
    return sale ? sale.discountAmount + sale.couponDiscountAmount + sale.pointsRedeemedAmount : 0;
  });
}
