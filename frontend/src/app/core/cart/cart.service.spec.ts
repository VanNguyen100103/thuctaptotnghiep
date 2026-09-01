import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { CartService } from './cart.service';

describe('CartService', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  const fakeCart = {
    id: 1,
    items: [],
    totalItems: 2,
    totalPrice: 100000,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  };

  it('enterStore() sets activeStoreSlug without an HTTP call for a guest', () => {
    const service = TestBed.inject(CartService);

    service.enterStore('shop-a');

    expect(service.activeStoreSlug()).toBe('shop-a');
    httpMock.expectNone(`${environment.apiUrl}/cart/count?storeSlug=shop-a`);
  });

  it('loadCart() sends storeSlug as a query param and updates cart()/itemCount()', () => {
    const service = TestBed.inject(CartService);

    service.loadCart('shop-a').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/cart`);
    expect(req.request.params.get('storeSlug')).toBe('shop-a');
    req.flush(fakeCart);

    expect(service.cart()).toEqual(fakeCart);
    expect(service.itemCount()).toBe(2);
    expect(service.isEmpty()).toBe(false);
  });

  it('addItem() posts to /cart/items and updates cart() from the response', () => {
    const service = TestBed.inject(CartService);

    service.addItem({ productId: 5, quantity: 1 }).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/cart/items`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ productId: 5, quantity: 1 });
    req.flush({ message: 'ok', cart: fakeCart, itemAdded: {} });

    expect(service.cart()).toEqual(fakeCart);
    expect(service.itemCount()).toBe(2);
  });

  it('updateItem() puts the new quantity and updates cart()', () => {
    const service = TestBed.inject(CartService);

    service.updateItem(7, 3).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/cart/items/7`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ quantity: 3 });
    req.flush({ message: 'ok', cart: fakeCart, itemUpdated: {} });

    expect(service.cart()).toEqual(fakeCart);
  });

  it('removeItem() deletes the item and updates cart()', () => {
    const service = TestBed.inject(CartService);

    service.removeItem(7).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/cart/items/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ message: 'ok', cart: { ...fakeCart, totalItems: 0 } });

    expect(service.itemCount()).toBe(0);
    expect(service.isEmpty()).toBe(true);
  });

  it('clearCart() sends storeSlug as a query param', () => {
    const service = TestBed.inject(CartService);

    service.clearCart('shop-a').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/cart/clear`);
    expect(req.request.method).toBe('DELETE');
    expect(req.request.params.get('storeSlug')).toBe('shop-a');
    req.flush({ message: 'ok', cart: { ...fakeCart, totalItems: 0, items: [] } });

    expect(service.itemCount()).toBe(0);
  });

  it('clearLocal() zeroes cart()/itemCount() without an HTTP call', () => {
    const service = TestBed.inject(CartService);

    service.clearLocal();

    expect(service.cart()).toBeNull();
    expect(service.itemCount()).toBe(0);
  });
});
