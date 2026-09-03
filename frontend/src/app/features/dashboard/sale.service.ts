import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CreateSaleRequest, SaleDTO } from './sale.models';

const BASE_URL = `${environment.apiUrl}/store/sales`;

@Injectable({ providedIn: 'root' })
export class SaleService {
  constructor(private readonly http: HttpClient) {}

  /** "Thanh toán" - finalizes the sale immediately (no draft step). */
  checkout(request: CreateSaleRequest): Observable<{ message: string; sale: SaleDTO }> {
    return this.http.post<{ message: string; sale: SaleDTO }>(BASE_URL, request);
  }
}
