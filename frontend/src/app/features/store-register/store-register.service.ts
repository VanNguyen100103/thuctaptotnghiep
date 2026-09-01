import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { RegisterStoreRequest, RegisterStoreResponse } from '../../core/auth/auth.models';

@Injectable({ providedIn: 'root' })
export class StoreRegisterService {
  constructor(private readonly http: HttpClient) {}

  register(request: RegisterStoreRequest): Observable<RegisterStoreResponse> {
    return this.http.post<RegisterStoreResponse>(`${environment.apiUrl}/stores/register`, request);
  }
}
