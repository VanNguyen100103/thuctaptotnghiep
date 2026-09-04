import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CouponValidation } from './coupon.models';

@Injectable({ providedIn: 'root' })
export class CouponService {
  constructor(private readonly http: HttpClient) {}

  /** "Mã coupon" live preview on the POS payment panel - the final discount is always re-priced server-side at checkout (see SaleService). */
  validate(code: string, orderSubtotal: number): Observable<CouponValidation> {
    return this.http.get<CouponValidation>(`${environment.apiUrl}/coupons/validate`, {
      params: { code, orderSubtotal },
    });
  }
}
