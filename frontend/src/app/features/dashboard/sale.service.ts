import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CreateSaleRequest, SaleDTO, SalePage } from './sale.models';

const BASE_URL = `${environment.apiUrl}/store/sales`;

@Injectable({ providedIn: 'root' })
export class SaleService {
  constructor(private readonly http: HttpClient) {}

  /** "Thanh toán" - finalizes the sale immediately (no draft step). */
  checkout(request: CreateSaleRequest): Observable<{ message: string; sale: SaleDTO }> {
    return this.http.post<{ message: string; sale: SaleDTO }>(BASE_URL, request);
  }

  /** "Hóa đơn" - every sale the register has run, newest first. */
  list(from: string | null, to: string | null, query: string, page = 0, size = 15): Observable<SalePage> {
    const params = new URLSearchParams();
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
    return this.http.get<SalePage>(`${BASE_URL}?${params.toString()}`);
  }

  getById(id: number): Observable<SaleDTO> {
    return this.http.get<SaleDTO>(`${BASE_URL}/${id}`);
  }
}
