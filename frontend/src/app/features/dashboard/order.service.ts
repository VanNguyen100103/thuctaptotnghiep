import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AllowedTransitions, StoreOrderDTO, StoreOrderPage, StoreOrderStatus } from './order.models';

const BASE_URL = `${environment.apiUrl}/store/orders`;

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

  list(
    statuses: StoreOrderStatus[],
    from: string | null,
    to: string | null,
    query: string,
    page = 0,
    size = 15,
  ): Observable<StoreOrderPage> {
    const params = new URLSearchParams();
    statuses.forEach((s) => params.append('statuses', s));
    if (from) {
      params.set('from', from);
    }
    if (to) {
      params.set('to', to);
    }
    if (query) {
      params.set('query', query);
    }
    params.set('page', String(page));
    params.set('size', String(size));
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
