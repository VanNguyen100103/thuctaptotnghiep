import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { switchMap, take, takeWhile, timer } from 'rxjs';

import { extractErrorMessage } from '../../../core/http/api-error';
import { ExecutePaymentResponse, isNoPaymentYet, PaymentDetail } from '../checkout.models';
import { clearPendingOrder, readPendingOrder } from '../pending-order.storage';
import { StorefrontPaymentService } from '../storefront-payment.service';

@Component({
  selector: 'app-payment-success',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './payment-success.html',
})
export class PaymentSuccess {
  private readonly route = inject(ActivatedRoute);
  private readonly paymentService = inject(StorefrontPaymentService);

  readonly storeSlug = signal<string | null>(null);
  readonly paypalResult = signal<ExecutePaymentResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly unknown = signal(false);

  // MoMo/COD path: no useful query params, so poll order status instead.
  readonly inPollingFlow = signal(false);
  readonly pollingActive = signal(false);
  readonly polledPayment = signal<PaymentDetail | null>(null);
  readonly paymentConfirmed = computed(() => this.polledPayment()?.status === 'COMPLETED');

  constructor() {
    const queryParams = this.route.snapshot.queryParamMap;
    const paymentId = queryParams.get('paymentId');
    const payerId = queryParams.get('PayerID');
    const pending = readPendingOrder();
    this.storeSlug.set(pending?.storeSlug ?? null);

    if (paymentId && payerId) {
      // PayPal capture leg - this call IS the confirmation.
      this.paymentService.executePayment({ paymentId, payerId }).subscribe({
        next: (data) => {
          this.paypalResult.set(data);
          clearPendingOrder();
        },
        error: (err: HttpErrorResponse) => this.errorMessage.set(extractErrorMessage(err)),
      });
      return;
    }

    if (!pending) {
      this.unknown.set(true);
      return;
    }

    this.pollOrderStatus(pending.orderId);
    clearPendingOrder();
  }

  private pollOrderStatus(orderId: number): void {
    this.inPollingFlow.set(true);
    this.pollingActive.set(true);

    timer(0, 2000)
      .pipe(
        take(5),
        switchMap(() => this.paymentService.getPaymentByOrder(orderId)),
        takeWhile((payment) => isNoPaymentYet(payment) || payment.status === 'PENDING', true),
      )
      .subscribe({
        next: (payment) => {
          if (!isNoPaymentYet(payment)) {
            this.polledPayment.set(payment);
          }
        },
        error: (err: HttpErrorResponse) => {
          this.pollingActive.set(false);
          this.errorMessage.set(extractErrorMessage(err));
        },
        complete: () => this.pollingActive.set(false),
      });
  }
}
