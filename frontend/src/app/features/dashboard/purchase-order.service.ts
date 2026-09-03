import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PurchaseOrderDTO, PurchaseOrderPage, PurchaseOrderStatus, SavePurchaseOrderRequest } from './purchase-order.models';

const BASE_URL = `${environment.apiUrl}/store/purchase-orders`;

@Injectable({ providedIn: 'root' })
export class PurchaseOrderService {
  constructor(private readonly http: HttpClient) {}

  /** Bumped after create/update/complete/cancel - the list route listens to this to refetch, same pattern as ProductAdminService.changed. */
  private readonly changedTick = signal(0);
  readonly changed = this.changedTick.asReadonly();
  notifyChanged(): void {
    this.changedTick.update((t) => t + 1);
  }

  list(
    statuses: PurchaseOrderStatus[],
    from: string | null,
    to: string | null,
    query: string,
    page = 0,
    size = 15,
  ): Observable<PurchaseOrderPage> {
    let params = new URLSearchParams();
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
    return this.http.get<PurchaseOrderPage>(`${BASE_URL}?${params.toString()}`);
  }

  getById(id: number): Observable<PurchaseOrderDTO> {
    return this.http.get<PurchaseOrderDTO>(`${BASE_URL}/${id}`);
  }

  create(request: SavePurchaseOrderRequest): Observable<{ message: string; purchaseOrder: PurchaseOrderDTO }> {
    return this.http.post<{ message: string; purchaseOrder: PurchaseOrderDTO }>(BASE_URL, request);
  }

  update(id: number, request: SavePurchaseOrderRequest): Observable<{ message: string; purchaseOrder: PurchaseOrderDTO }> {
    return this.http.put<{ message: string; purchaseOrder: PurchaseOrderDTO }>(`${BASE_URL}/${id}`, request);
  }

  complete(id: number): Observable<{ message: string; purchaseOrder: PurchaseOrderDTO }> {
    return this.http.patch<{ message: string; purchaseOrder: PurchaseOrderDTO }>(`${BASE_URL}/${id}/complete`, {});
  }

  cancel(id: number): Observable<{ message: string; purchaseOrder: PurchaseOrderDTO }> {
    return this.http.patch<{ message: string; purchaseOrder: PurchaseOrderDTO }>(`${BASE_URL}/${id}/cancel`, {});
  }
}
