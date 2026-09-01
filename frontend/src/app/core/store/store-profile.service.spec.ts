import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { StoreProfileService } from './store-profile.service';

describe('StoreProfileService', () => {
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

  it('getCurrentStore() calls GET /store', () => {
    const service = TestBed.inject(StoreProfileService);

    service.getCurrentStore().subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/store`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: 1, name: 'Shop A', slug: 'shop-a', logoUrl: '', phone: '', address: '' });
  });
});
