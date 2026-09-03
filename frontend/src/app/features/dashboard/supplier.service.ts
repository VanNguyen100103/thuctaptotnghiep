import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SupplierDTO, SupplierRequest } from './supplier.models';

const BASE_URL = `${environment.apiUrl}/store/suppliers`;

@Injectable({ providedIn: 'root' })
export class SupplierService {
  constructor(private readonly http: HttpClient) {}

  /** No query = full active list, sorted by name - suppliers are expected to number in the tens/low hundreds per store. */
  list(query?: string): Observable<{ suppliers: SupplierDTO[] }> {
    const params = query ? { query } : undefined;
    return this.http.get<{ suppliers: SupplierDTO[] }>(BASE_URL, { params });
  }

  create(request: SupplierRequest): Observable<{ message: string; supplier: SupplierDTO }> {
    return this.http.post<{ message: string; supplier: SupplierDTO }>(BASE_URL, request);
  }

  update(id: number, request: SupplierRequest): Observable<{ message: string; supplier: SupplierDTO }> {
    return this.http.put<{ message: string; supplier: SupplierDTO }>(`${BASE_URL}/${id}`, request);
  }

  delete(id: number): Observable<{ message: string; supplierId: number }> {
    return this.http.delete<{ message: string; supplierId: number }>(`${BASE_URL}/${id}`);
  }
}
