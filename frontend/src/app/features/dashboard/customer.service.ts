import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CustomerDTO, CustomerRequest } from './customer.models';

const BASE_URL = `${environment.apiUrl}/store/customers`;

@Injectable({ providedIn: 'root' })
export class CustomerService {
  constructor(private readonly http: HttpClient) {}

  /** No query = full active list, sorted by name - used both as the default "Tìm khách hàng (F4)" list and its filtered search. */
  list(query?: string): Observable<{ customers: CustomerDTO[] }> {
    const params = query ? { query } : undefined;
    return this.http.get<{ customers: CustomerDTO[] }>(BASE_URL, { params });
  }

  create(request: CustomerRequest): Observable<{ message: string; customer: CustomerDTO }> {
    return this.http.post<{ message: string; customer: CustomerDTO }>(BASE_URL, request);
  }
}
