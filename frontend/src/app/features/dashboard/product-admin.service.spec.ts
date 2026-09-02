import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ProductAdminService } from './product-admin.service';

describe('ProductAdminService', () => {
  let httpMock: HttpTestingController;
  const BASE = `${environment.apiUrl}/store/products`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list() sends page/size/sortBy/sortDirection and omits active when unset', () => {
    const service = TestBed.inject(ProductAdminService);

    service.list(1, 10, 'price', 'ASC').subscribe();

    const req = httpMock.expectOne((r) => r.url === BASE);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sortBy')).toBe('price');
    expect(req.request.params.get('sortDirection')).toBe('ASC');
    expect(req.request.params.has('active')).toBe(false);
    req.flush({ products: [], currentPage: 1, totalItems: 0, totalPages: 0 });
  });

  it('list() sends active when provided', () => {
    const service = TestBed.inject(ProductAdminService);

    service.list(0, 20, 'createdAt', 'DESC', true).subscribe();

    const req = httpMock.expectOne((r) => r.url === BASE);
    expect(req.request.params.get('active')).toBe('true');
    req.flush({ products: [], currentPage: 0, totalItems: 0, totalPages: 0 });
  });

  it('search() sends the query', () => {
    const service = TestBed.inject(ProductAdminService);

    service.search('ao thun').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${BASE}/search`);
    expect(req.request.params.get('query')).toBe('ao thun');
    req.flush({ products: [], currentPage: 0, totalItems: 0, totalPages: 0, query: 'ao thun' });
  });

  it('getById() calls GET /store/products/{id}', () => {
    const service = TestBed.inject(ProductAdminService);

    service.getById(5).subscribe();

    const req = httpMock.expectOne(`${BASE}/5`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('getStats() calls GET /store/products/stats', () => {
    const service = TestBed.inject(ProductAdminService);

    service.getStats().subscribe();

    const req = httpMock.expectOne(`${BASE}/stats`);
    expect(req.request.method).toBe('GET');
    req.flush({ totalProducts: 0, activeProducts: 0, inactiveProducts: 0, outOfStock: 0 });
  });

  it('create() posts to /store/products', () => {
    const service = TestBed.inject(ProductAdminService);
    const request = { name: 'Áo thun', slug: 'ao-thun', sku: 'SKU1', price: 100000 };

    service.create(request).subscribe();

    const req = httpMock.expectOne(BASE);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ message: 'ok', product: {} });
  });

  it('update() puts to /store/products/{id}', () => {
    const service = TestBed.inject(ProductAdminService);

    service.update(5, { name: 'New name' }).subscribe();

    const req = httpMock.expectOne(`${BASE}/5`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ name: 'New name' });
    req.flush({ message: 'ok', product: {} });
  });

  it('updateStock() patches stockQuantity', () => {
    const service = TestBed.inject(ProductAdminService);

    service.updateStock(5, 10).subscribe();

    const req = httpMock.expectOne(`${BASE}/5/stock`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ stockQuantity: 10 });
    req.flush({ message: 'ok', productId: 5, stockQuantity: 10 });
  });

  it('updateStatus() patches active', () => {
    const service = TestBed.inject(ProductAdminService);

    service.updateStatus(5, false).subscribe();

    const req = httpMock.expectOne(`${BASE}/5/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ active: false });
    req.flush({ message: 'ok', productId: 5, active: false });
  });

  it('delete() sends DELETE', () => {
    const service = TestBed.inject(ProductAdminService);

    service.delete(5).subscribe();

    const req = httpMock.expectOne(`${BASE}/5`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ message: 'ok', productId: 5 });
  });

  it('replaceCategories() patches categoryIds', () => {
    const service = TestBed.inject(ProductAdminService);

    service.replaceCategories(5, [1, 2]).subscribe();

    const req = httpMock.expectOne(`${BASE}/5/categories`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ categoryIds: [1, 2] });
    req.flush({ message: 'ok', productId: 5, categories: [] });
  });
});
