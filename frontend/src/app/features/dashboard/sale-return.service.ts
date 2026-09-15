import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreateSaleReturnRequest,
  ReturnableSaleDTO,
  SaleReturnDTO,
  SaleReturnPage,
} from './sale-return.models';

const BASE_URL = `${environment.apiUrl}/store/sale-returns`;

/**
 * Everything the "Trả hàng" list can narrow by. One object rather than a row
 * of positional strings, for the same reason SaleListQuery is one.
 */
export interface SaleReturnListQuery {
  from: string | null;
  to: string | null;
  /** "Theo mã trả hàng". */
  code: string;
  saleCode: string;
  customer: string;
  product: string;
  note: string;
  refundMethods: string[];
  /** "Người trả hàng" - a username from creators(), or '' for everyone. */
  createdBy: string;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class SaleReturnService {
  constructor(private readonly http: HttpClient) {}

  list(query: SaleReturnListQuery): Observable<SaleReturnPage> {
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
    if (query.saleCode) {
      params.set('saleCode', query.saleCode);
    }
    if (query.customer) {
      params.set('customer', query.customer);
    }
    if (query.product) {
      params.set('product', query.product);
    }
    if (query.note) {
      params.set('note', query.note);
    }
    if (query.createdBy) {
      params.set('createdBy', query.createdBy);
    }
    query.refundMethods.forEach((method) => params.append('refundMethods', method));
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    return this.http.get<SaleReturnPage>(`${BASE_URL}?${params.toString()}`);
  }

  /** "Người trả hàng" options - only whoever has actually run a return. */
  creators(): Observable<{ creators: string[] }> {
    return this.http.get<{ creators: string[] }>(`${BASE_URL}/creators`);
  }

  getById(id: number): Observable<SaleReturnDTO> {
    return this.http.get<SaleReturnDTO>(`${BASE_URL}/${id}`);
  }

  /** The invoice the form opens on, with each line's remaining returnable quantity. */
  returnable(saleId: number): Observable<ReturnableSaleDTO> {
    return this.http.get<ReturnableSaleDTO>(`${BASE_URL}/returnable/${saleId}`);
  }

  /** "Trả hàng" - writes the document, restocks and names the refund in one call. */
  create(request: CreateSaleReturnRequest): Observable<{ message: string; saleReturn: SaleReturnDTO }> {
    return this.http.post<{ message: string; saleReturn: SaleReturnDTO }>(BASE_URL, request);
  }
}
