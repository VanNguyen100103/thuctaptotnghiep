import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CreateSaleRequest, SaleDTO, SalePage } from './sale.models';

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
  paymentMethods: string[];
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class SaleService {
  constructor(private readonly http: HttpClient) {}

  /** "Thanh toán" - finalizes the sale immediately (no draft step). */
  checkout(request: CreateSaleRequest): Observable<{ message: string; sale: SaleDTO }> {
    return this.http.post<{ message: string; sale: SaleDTO }>(BASE_URL, request);
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
    query.paymentMethods.forEach((method) => params.append('paymentMethods', method));
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    return this.http.get<SalePage>(`${BASE_URL}?${params.toString()}`);
  }

  getById(id: number): Observable<SaleDTO> {
    return this.http.get<SaleDTO>(`${BASE_URL}/${id}`);
  }
}
