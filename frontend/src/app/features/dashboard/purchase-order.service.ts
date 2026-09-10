import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  PurchaseOrderDTO,
  PurchaseOrderListQuery,
  PurchaseOrderPage,
  SavePurchaseOrderRequest,
} from './purchase-order.models';

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

  list(query: PurchaseOrderListQuery): Observable<PurchaseOrderPage> {
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
    if (query.supplier) {
      params.set('supplier', query.supplier);
    }
    if (query.note) {
      params.set('note', query.note);
    }
    if (query.createdBy) {
      params.set('createdBy', query.createdBy);
    }
    if (query.completedBy) {
      params.set('completedBy', query.completedBy);
    }
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    return this.http.get<PurchaseOrderPage>(`${BASE_URL}?${params.toString()}`);
  }

  /** "Người tạo"/"Người nhập" options - only people who have actually raised or received a receipt. */
  people(): Observable<{ creators: string[]; receivers: string[] }> {
    return this.http.get<{ creators: string[]; receivers: string[] }>(`${BASE_URL}/people`);
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

  /** "Đánh dấu" - the star column. A bookmark only; it changes nothing else on the receipt. */
  setStarred(id: number, starred: boolean): Observable<{ id: number; starred: boolean }> {
    return this.http.patch<{ id: number; starred: boolean }>(`${BASE_URL}/${id}/star`, { starred });
  }
}
