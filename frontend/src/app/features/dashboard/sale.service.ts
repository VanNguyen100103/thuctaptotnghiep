import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CheckoutResponse, CreateSaleRequest, SaleDTO, SalePage } from './sale.models';

const BASE_URL = `${environment.apiUrl}/store/sales`;

/**
 * Everything the "Hóa đơn" list can narrow by. Passed as one object rather
 * than a row of positional strings - there are four search boxes now, and
 * `list(from, to, code, product, customer, note, ...)` is a bug waiting for
 * the day two of them get swapped.
 */
export interface SaleListQuery {
  from: string | null;
  to: string | null;
  code: string;
  product: string;
  customer: string;
  note: string;
  /** The four advanced-search boxes for things a POS invoice does not carry - see SaleController. */
  einvoiceNumber: string;
  trackingCode: string;
  orderCode: string;
  itemNote: string;
  paymentMethods: string[];
  invoiceTypes: string[];
  invoiceStatuses: string[];
  einvoiceStatuses: string[];
  deliveryStatuses: string[];
  deliveryPartners: string[];
  /** "Người bán" - a username from SaleService#sellers, or '' for every seller. */
  seller: string;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class SaleService {
  constructor(private readonly http: HttpClient) {}

  /** "Thanh toán" - finalizes the sale immediately (no draft step). */
  /**
   * "Bán giao hàng" also raises an order, and the response names it: booking
   * the parcel is a second call, and it has to say which order it carries.
   */
  checkout(request: CreateSaleRequest): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(BASE_URL, request);
  }

  /** "Hóa đơn" - every sale the register has run, newest first. */
  list(query: SaleListQuery): Observable<SalePage> {
    const params = new URLSearchParams();
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
    if (query.note) {
      params.set('note', query.note);
    }
    if (query.einvoiceNumber) {
      params.set('einvoiceNumber', query.einvoiceNumber);
    }
    if (query.trackingCode) {
      params.set('trackingCode', query.trackingCode);
    }
    if (query.orderCode) {
      params.set('orderCode', query.orderCode);
    }
    if (query.itemNote) {
      params.set('itemNote', query.itemNote);
    }
    if (query.seller) {
      params.set('seller', query.seller);
    }
    query.paymentMethods.forEach((method) => params.append('paymentMethods', method));
    query.invoiceTypes.forEach((type) => params.append('invoiceTypes', type));
    query.invoiceStatuses.forEach((status) => params.append('invoiceStatuses', status));
    query.einvoiceStatuses.forEach((status) => params.append('einvoiceStatuses', status));
    query.deliveryStatuses.forEach((status) => params.append('deliveryStatuses', status));
    query.deliveryPartners.forEach((partner) => params.append('deliveryPartners', partner));
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    return this.http.get<SalePage>(`${BASE_URL}?${params.toString()}`);
  }

  /** "Người bán" options - only the cashiers who have actually rung something up. */
  sellers(): Observable<{ sellers: string[] }> {
    return this.http.get<{ sellers: string[] }>(`${BASE_URL}/sellers`);
  }

  getById(id: number): Observable<SaleDTO> {
    return this.http.get<SaleDTO>(`${BASE_URL}/${id}`);
  }
}
