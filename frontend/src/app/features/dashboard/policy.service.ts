import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PolicyDTO, PolicyRequest } from './policy.models';

const BASE_URL = `${environment.apiUrl}/store/policies`;

@Injectable({ providedIn: 'root' })
export class PolicyService {
  constructor(private readonly http: HttpClient) {}

  list(): Observable<{ policies: PolicyDTO[] }> {
    return this.http.get<{ policies: PolicyDTO[] }>(BASE_URL);
  }

  create(request: PolicyRequest): Observable<{ message: string; policy: PolicyDTO }> {
    return this.http.post<{ message: string; policy: PolicyDTO }>(BASE_URL, request);
  }

  update(id: number, request: PolicyRequest): Observable<{ message: string; policy: PolicyDTO }> {
    return this.http.put<{ message: string; policy: PolicyDTO }>(`${BASE_URL}/${id}`, request);
  }

  delete(id: number): Observable<{ message: string; policyId: number }> {
    return this.http.delete<{ message: string; policyId: number }>(`${BASE_URL}/${id}`);
  }
}
