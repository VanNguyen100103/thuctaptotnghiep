import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { StorefrontCatalogService } from './storefront-catalog.service';

describe('StorefrontCatalogService', () => {
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

  it('getStore() calls GET /stores/{slug}', () => {
    const service = TestBed.inject(StorefrontCatalogService);

    service.getStore('shop-a').subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/stores/shop-a`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: 1, name: 'Shop A', slug: 'shop-a', logoUrl: null, phone: null, address: null });
  });

  it('getProducts() sends page/size/sortBy/sortDirection as query params', () => {
    const service = TestBed.inject(StorefrontCatalogService);

    service.getProducts('shop-a', 1, 10, 'price', 'ASC').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/stores/shop-a/products`);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sortBy')).toBe('price');
    expect(req.request.params.get('sortDirection')).toBe('ASC');
    req.flush({ products: [], currentPage: 1, totalItems: 0, totalPages: 0 });
  });

  it('getProduct() calls GET /stores/{slug}/products/{id}', () => {
    const service = TestBed.inject(StorefrontCatalogService);

    service.getProduct('shop-a', 42).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/stores/shop-a/products/42`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('getCategories() calls GET /stores/{slug}/categories', () => {
    const service = TestBed.inject(StorefrontCatalogService);

    service.getCategories('shop-a').subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/stores/shop-a/categories`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
