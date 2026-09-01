import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CheckoutRequest,
  CheckoutResponse,
  CouponValidation,
  CreatePaymentRequest,
  CreatePaymentResponse,
  ExecutePaymentRequest,
  ExecutePaymentResponse,
  NoPaymentYet,
  OrderDetail,
  PaymentDetail,
} from './checkout.models';

@Injectable({ providedIn: 'root' })
export class StorefrontPaymentService {
  constructor(private readonly http: HttpClient) {}

  validateCoupon(code: string, orderSubtotal: number): Observable<CouponValidation> {
    return this.http.get<CouponValidation>(`${environment.apiUrl}/coupons/validate`, {
      params: { code, orderSubtotal },
    });
  }

  checkout(request: CheckoutRequest): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${environment.apiUrl}/orders/checkout`, request);
  }

  createPayment(request: CreatePaymentRequest): Observable<CreatePaymentResponse> {
    return this.http.post<CreatePaymentResponse>(`${environment.apiUrl}/payments/create`, request);
  }

  executePayment(request: ExecutePaymentRequest): Observable<ExecutePaymentResponse> {
    return this.http.post<ExecutePaymentResponse>(`${environment.apiUrl}/payments/execute`, request);
  }

  getPaymentByOrder(orderId: number): Observable<PaymentDetail | NoPaymentYet> {
    return this.http.get<PaymentDetail | NoPaymentYet>(`${environment.apiUrl}/payments/order/${orderId}`);
  }

  getOrder(orderId: number): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${environment.apiUrl}/orders/${orderId}`);
  }
}
