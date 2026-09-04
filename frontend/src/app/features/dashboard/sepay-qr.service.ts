import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface VietQrResponse {
  qrUrl: string;
}

@Injectable({ providedIn: 'root' })
export class SepayQrService {
  constructor(private readonly http: HttpClient) {}

  /** VietQR image for a "Chuyển khoản" split-tender line - display-only, matches SePayPaymentProvider#buildQrUrl on the backend. */
  getQr(amount: number): Observable<VietQrResponse> {
    return this.http.get<VietQrResponse>(`${environment.apiUrl}/payments/vietqr`, {
      params: { amount },
    });
  }
}
