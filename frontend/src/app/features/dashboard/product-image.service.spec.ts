import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ProductImageService } from './product-image.service';

describe('ProductImageService', () => {
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

  it('upload() posts a multipart FormData with "images" parts and an optional color param', () => {
    const service = TestBed.inject(ProductImageService);
    const file = new File(['data'], 'photo.jpg', { type: 'image/jpeg' });

    service.upload(5, [file], 'Đỏ').subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/products/5/images`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('color')).toBe('Đỏ');
    expect(req.request.body instanceof FormData).toBe(true);
    expect((req.request.body as FormData).getAll('images')).toEqual([file]);
    req.flush({ message: 'ok', product: {} });
  });

  it('upload() omits the color param when not given', () => {
    const service = TestBed.inject(ProductImageService);
    const file = new File(['data'], 'photo.jpg', { type: 'image/jpeg' });

    service.upload(5, [file]).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/products/5/images`);
    expect(req.request.params.has('color')).toBe(false);
    req.flush({ message: 'ok', product: {} });
  });

  it('delete() sends DELETE to /products/images/{imageId}', () => {
    const service = TestBed.inject(ProductImageService);

    service.delete(9).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/products/images/9`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ message: 'ok' });
  });
});
