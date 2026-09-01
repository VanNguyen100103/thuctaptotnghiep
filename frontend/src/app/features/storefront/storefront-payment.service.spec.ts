import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { StorefrontPaymentService } from './storefront-payment.service';

describe('StorefrontPaymentService', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('validateCoupon() sends code/orderSubtotal as query params', () => {
    const service = TestBed.inject(StorefrontPaymentService);

    service.validateCoupon('SUMMER2025', 200000).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/coupons/validate`);
    expect(req.request.params.get('code')).toBe('SUMMER2025');
    expect(req.request.params.get('orderSubtotal')).toBe('200000');
    req.flush({ valid: true });
  });

  it('checkout() posts to /orders/checkout with storeSlug', () => {
    const service = TestBed.inject(StorefrontPaymentService);
    const request = {
      shippingAddress: {
        addressLine1: '123 Le Loi',
        city: 'HCMC',
        stateProvince: 'HCMC',
        postalCode: '700000',
        country: 'Vietnam',
      },
      email: 'customer@example.com',
      storeSlug: 'shop-a',
    };

    service.checkout(request).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/orders/checkout`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ message: 'ok', order: {}, couponApplied: false, discountAmount: 0 });
  });

  it('createPayment() posts orderId + paymentMethod', () => {
    const service = TestBed.inject(StorefrontPaymentService);

    service.createPayment({ orderId: 1, paymentMethod: 'CASH_ON_DELIVERY' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/payments/create`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ orderId: 1, paymentMethod: 'CASH_ON_DELIVERY' });
    req.flush({ message: 'ok', paymentId: 1, paymentMethod: 'CASH_ON_DELIVERY', redirectUrl: '/payment/success', status: 'PENDING' });
  });

  it('executePayment() posts paymentId + payerId', () => {
    const service = TestBed.inject(StorefrontPaymentService);

    service.executePayment({ paymentId: 'PAY-1', payerId: 'PAYER-1' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/payments/execute`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ paymentId: 'PAY-1', payerId: 'PAYER-1' });
    req.flush({ message: 'ok', paymentId: 'PAY-1', transactionId: 'SALE-1', status: 'COMPLETED', orderNumber: 'ORD-1', orderStatus: 'PAID' });
  });

  it('getPaymentByOrder() calls GET /payments/order/{orderId}', () => {
    const service = TestBed.inject(StorefrontPaymentService);

    service.getPaymentByOrder(9).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/payments/order/9`);
    expect(req.request.method).toBe('GET');
    req.flush({ message: 'No payment found for this order', hasPayment: false });
  });

  it('getOrder() calls GET /orders/{orderId}', () => {
    const service = TestBed.inject(StorefrontPaymentService);

    service.getOrder(9).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/orders/9`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
