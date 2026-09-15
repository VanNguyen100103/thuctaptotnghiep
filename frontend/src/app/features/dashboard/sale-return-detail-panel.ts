import { DatePipe } from '@angular/common';
import { Component, inject, input, output } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import { SALE_PAYMENT_METHOD_LABELS } from './sale.models';
import { SaleReturnDTO } from './sale-return.models';
import { SaleReturnService } from './sale-return.service';

/**
 * The panel that opens inside the "Trả hàng" list when a row is clicked. Like
 * the Hóa đơn panel it only reads: a return is final the moment the money
 * goes back over the counter (see backend SaleReturn), so there is no status
 * to move it through and nothing here to edit.
 */
@Component({
  selector: 'app-sale-return-detail-panel',
  standalone: true,
  imports: [RouterLink, DatePipe, VndCurrencyPipe],
  templateUrl: './sale-return-detail-panel.html',
})
export class SaleReturnDetailPanel {
  private readonly saleReturnService = inject(SaleReturnService);

  readonly saleReturnId = input.required<number>();
  readonly closed = output<void>();

  readonly paymentMethodLabels = SALE_PAYMENT_METHOD_LABELS;

  readonly detailState = toSignal(
    toObservable(this.saleReturnId).pipe(
      switchMap((id) => toApiState<SaleReturnDTO>(this.saleReturnService.getById(id))),
    ),
    { initialValue: INITIAL_API_STATE },
  );
}
