import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AllowedTransitions, StoreOrderDTO, StoreOrderPage, StoreOrderStatus } from './order.models';

const BASE_URL = `${environment.apiUrl}/store/orders`;

/** Everything the "Đặt hàng" list can narrow by - see SaleListQuery for why this is one object. */
export interface OrderListQuery {
  statuses: StoreOrderStatus[];
  from: string | null;
  to: string | null;
  code: string;
  product: string;
  customer: string;
  tracking: string;
  note: string;
  paymentMethods: string[];
  page: number;
  size: number;
}

/** The dashboard side of customer orders - KiotViet's "Đặt hàng". The storefront's own order calls live in StorefrontPaymentService. */
@Injectable({ providedIn: 'root' })
export class OrderService {
  constructor(private readonly http: HttpClient) {}

  /** Bumped after a status change or a cancel - the list refetches on it, same pattern as PurchaseOrderService.changed. */
  private readonly changedTick = signal(0);
  readonly changed = this.changedTick.asReadonly();
  notifyChanged(): void {
    this.changedTick.update((t) => t + 1);
  }

  list(query: OrderListQuery): Observable<StoreOrderPage> {
    const params = new URLSearchParams();
    query.statuses.forEach((status) => params.append('statuses', status));
    if (query.from) {
      params.set('from', query.from);
    }
    if (query.to) {
      params.set('to', query.to);
    }
    if (query.code) {
      params.set('query', query.code);
    }
    if (query.product) {
      params.set('product', query.product);
    }
    if (query.customer) {
      params.set('customer', query.customer);
    }
    if (query.tracking) {
      params.set('tracking', query.tracking);
    }
    if (query.note) {
      params.set('note', query.note);
    }
    query.paymentMethods.forEach((method) => params.append('paymentMethods', method));
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    return this.http.get<StoreOrderPage>(`${BASE_URL}?${params.toString()}`);
  }

  getById(id: number): Observable<StoreOrderDTO> {
    return this.http.get<StoreOrderDTO>(`${BASE_URL}/${id}`);
  }

  /** What the order may legally move to next - the backend owns those rules (OrderStatusValidator), so the panel asks rather than guesses. */
  allowedTransitions(id: number): Observable<AllowedTransitions> {
    return this.http.get<AllowedTransitions>(`${BASE_URL}/${id}/allowed-transitions`);
  }

  updateStatus(id: number, status: StoreOrderStatus): Observable<{ message: string }> {
    return this.http.patch<{ message: string }>(`${BASE_URL}/${id}/status`, { status });
  }

  cancel(id: number, reason: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${BASE_URL}/${id}/cancel`, { reason });
  }
}
