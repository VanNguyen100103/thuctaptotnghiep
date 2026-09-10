import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AllowedTransitions,
  BulkOrderResult,
  DeliveryArea,
  MergeOrdersResult,
  StoreOrderDTO,
  StoreOrderPage,
  StoreOrderStatus,
} from './order.models';

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
  /** "Trạng thái giao hàng" - Goship's own codes, finer than this system's order statuses. */
  deliveryStatuses: number[];
  /** "Đối tác giao hàng" - matched against the carrier written on the order. */
  carriers: string[];
  /** "Kênh bán". */
  channels: string[];
  /** "Người tạo" - usernames. */
  creators: string[];
  /** "Khu vực giao hàng" - Tỉnh/TP and, under it, Quận/Huyện. */
  province: string | null;
  district: string | null;
  /** "Thời gian giao hàng" - the promised date, not the order date. */
  deliveryFrom: string | null;
  deliveryTo: string | null;
  page: number;
  size: number;
}

/**
 * What asking the carrier produced. `changed` separates "the parcel has not
 * moved" from "the call did not work" - without it, both look like nothing
 * happening.
 */
export interface RefreshDeliveryResult {
  orderId: number;
  status: string;
  shipmentStatus: string;
  changed: boolean;
  message: string;
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
    query.deliveryStatuses.forEach((code) => params.append('deliveryStatuses', String(code)));
    query.carriers.forEach((carrier) => params.append('carriers', carrier));
    query.channels.forEach((channel) => params.append('channels', channel));
    query.creators.forEach((creator) => params.append('creators', creator));
    if (query.province) {
      params.set('province', query.province);
    }
    if (query.district) {
      params.set('district', query.district);
    }
    if (query.deliveryFrom) {
      params.set('deliveryFrom', query.deliveryFrom);
    }
    if (query.deliveryTo) {
      params.set('deliveryTo', query.deliveryTo);
    }
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

  /** The star column. */
  setStarred(id: number, starred: boolean): Observable<{ orderId: number; starred: boolean }> {
    return this.http.patch<{ orderId: number; starred: boolean }>(`${BASE_URL}/${id}/star`, { starred });
  }

  // ---- the selection toolbar ----

  /** "Xử lý đặt hàng" / "Kết thúc" - move every ticked order to one status. */
  bulkStatus(ids: number[], status: StoreOrderStatus): Observable<BulkOrderResult> {
    return this.http.post<BulkOrderResult>(`${BASE_URL}/bulk-status?status=${status}`, { ids });
  }

  /** "Hủy đơn". */
  bulkCancel(ids: number[], reason: string): Observable<BulkOrderResult> {
    return this.http.post<BulkOrderResult>(`${BASE_URL}/bulk-cancel`, { ids, reason });
  }

  /**
   * "Sửa người nhận đặt, kênh bán, ghi chú". A field left null is left alone
   * on every order; a blank one clears it - which is the difference between
   * "I only came here to set the channel" and "delete these notes".
   */
  bulkUpdate(
    ids: number[],
    changes: { recipientName?: string | null; salesChannel?: string | null; notes?: string | null },
  ): Observable<BulkOrderResult> {
    return this.http.post<BulkOrderResult>(`${BASE_URL}/bulk-update`, { ids, ...changes });
  }

  /** "Gộp đơn" - fold several of one customer's unpaid orders into a single one. */
  merge(ids: number[]): Observable<MergeOrdersResult> {
    return this.http.post<MergeOrdersResult>(`${BASE_URL}/merge`, { ids });
  }

  /**
   * "Cập nhật trạng thái" - ask the carrier where this order's parcel is now.
   * The scheduled sweep and the webhook both do this on their own; this is for
   * somebody looking at the order who wants the answer without waiting.
   */
  refreshDelivery(id: number): Observable<RefreshDeliveryResult> {
    return this.http.patch<RefreshDeliveryResult>(`${BASE_URL}/${id}/refresh-delivery`, {});
  }

  /** "Người tạo" - only the staff who have actually raised an order here. */
  creators(): Observable<{ creators: string[] }> {
    return this.http.get<{ creators: string[] }>(`${BASE_URL}/creators`);
  }

  /** "Khu vực giao hàng" - the Tỉnh/TP and Quận/Huyện this store has actually shipped to. */
  deliveryAreas(): Observable<{ areas: DeliveryArea[] }> {
    return this.http.get<{ areas: DeliveryArea[] }>(`${BASE_URL}/delivery-areas`);
  }
}
