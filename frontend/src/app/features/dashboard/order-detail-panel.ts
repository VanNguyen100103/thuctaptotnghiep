import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { ActionErrorBanner } from './action-error-banner';
import { INITIAL_API_STATE, toApiState } from './api-state.util';
import {
  AllowedTransitions,
  ORDER_PAYMENT_METHOD_LABELS,
  ORDER_PAYMENT_STATUS_LABELS,
  ORDER_STATUS_LABELS,
  SALES_CHANNEL_LABELS,
  SALE_TENDER_LABELS,
  SalesChannel,
  StoreOrderDTO,
  StoreOrderDeliveryDTO,
  StoreOrderStatus,
} from './order.models';
import { OrderService } from './order.service';
import { ActionError, toActionError } from './subscription-error.util';

/**
 * The panel that opens inside the "Đặt hàng" list when a row is clicked -
 * KiotViet's own detail view: the order's information above its lines, the
 * money stacked on the right, and an action bar along the bottom.
 *
 * Which buttons the action bar offers is not decided here: the backend owns
 * the order lifecycle (OrderStatusValidator), so the panel asks it what this
 * order may become next and renders exactly that.
 */
@Component({
  selector: 'app-order-detail-panel',
  standalone: true,
  imports: [DatePipe, RouterLink, VndCurrencyPipe, ActionErrorBanner],
  templateUrl: './order-detail-panel.html',
})
export class OrderDetailPanel {
  private readonly orderService = inject(OrderService);

  readonly orderId = input.required<number>();
  readonly closed = output<void>();

  readonly statusLabels = ORDER_STATUS_LABELS;

  channelLabel(channel: SalesChannel): string {
    return SALES_CHANNEL_LABELS[channel] ?? channel;
  }

  /** "GHTK - Tiết kiệm", or just the carrier when no booking exists to name a service. */
  deliveryServiceLabel(order: StoreOrderDTO): string {
    const delivery = order.delivery;
    const carrier = delivery?.carrierShortName || delivery?.carrierName || order.shippingCarrier;
    if (!carrier) {
      return '';
    }
    return delivery?.service ? `${carrier} - ${delivery.service}` : carrier;
  }

  /** "Người gửi trả phí" vs the courier collecting it at the door. */
  feePayerLabel(delivery: StoreOrderDeliveryDTO): string {
    return delivery.senderPaysShipping ? 'Người gửi trả phí' : 'Người nhận trả phí';
  }

  readonly detailState = toSignal(
    toObservable(computed(() => ({ id: this.orderId(), tick: this.orderService.changed() }))).pipe(
      switchMap(({ id }) => toApiState<StoreOrderDTO>(this.orderService.getById(id))),
    ),
    { initialValue: INITIAL_API_STATE },
  );

  readonly transitionsState = toSignal(
    toObservable(computed(() => ({ id: this.orderId(), tick: this.orderService.changed() }))).pipe(
      switchMap(({ id }) => toApiState<AllowedTransitions>(this.orderService.allowedTransitions(id))),
    ),
    { initialValue: INITIAL_API_STATE },
  );

  /** Sorted so the buttons keep a stable order between refetches - the backend hands these back as a Set. */
  readonly nextStatuses = computed<StoreOrderStatus[]>(() => {
    const transitions = this.transitionsState().data?.allowedTransitions ?? [];
    return [...transitions].sort();
  });

  readonly canCancel = computed(() => this.nextStatuses().includes('CANCELLED'));

  /** "Còn phải trả" - what the shop is still chasing on this order. */
  readonly outstanding = computed(() => {
    const order = this.detailState().data;
    return order ? Math.max(order.total - order.amountPaid, 0) : 0;
  });

  readonly saving = signal(false);
  readonly actionError = signal<ActionError | null>(null);

  /**
   * A storefront order names a gateway; a register order names the till
   * tender its invoice was rung up with, which is a different enum. Both
   * reach this screen, so both tables are consulted.
   */
  paymentMethodLabel(method: string | null): string {
    if (!method) {
      return '—';
    }
    return (
      ORDER_PAYMENT_METHOD_LABELS[method as keyof typeof ORDER_PAYMENT_METHOD_LABELS] ??
      SALE_TENDER_LABELS[method] ??
      method
    );
  }

  paymentStatusLabel(status: string | null): string {
    return status ? (ORDER_PAYMENT_STATUS_LABELS[status] ?? status) : '—';
  }

  /** Every transition except cancelling, which the action bar draws separately (and asks a reason for). */
  advanceStatuses(): StoreOrderStatus[] {
    return this.nextStatuses().filter((s) => s !== 'CANCELLED');
  }

  applyStatus(status: StoreOrderStatus): void {
    this.saving.set(true);
    this.actionError.set(null);
    this.orderService.updateStatus(this.orderId(), status).subscribe({
      next: () => {
        this.saving.set(false);
        this.orderService.notifyChanged();
      },
      error: (err) => {
        this.saving.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }

  /** The reason is written into the order's admin notes, so "why" survives the cancel. */
  cancelOrder(): void {
    const reason = window.prompt('Lý do hủy đơn hàng:');
    if (reason === null) {
      return;
    }
    this.saving.set(true);
    this.actionError.set(null);
    this.orderService.cancel(this.orderId(), reason.trim()).subscribe({
      next: () => {
        this.saving.set(false);
        this.orderService.notifyChanged();
      },
      error: (err) => {
        this.saving.set(false);
        this.actionError.set(toActionError(err));
      },
    });
  }
}
