import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

function base64url(input: string): string {
  return btoa(input).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fakeToken(payload: object): string {
  const header = base64url(JSON.stringify({ alg: 'HS512', typ: 'JWT' }));
  const body = base64url(JSON.stringify(payload));
  return `${header}.${body}.fake-signature`;
}

describe('AuthService', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts unauthenticated when localStorage is empty', () => {
    const service = TestBed.inject(AuthService);
    expect(service.isAuthenticated()).toBe(false);
    expect(service.currentUser()).toBeNull();
  });

  it('rehydrates a valid, non-expired session from localStorage on construction', () => {
    const token = fakeToken({
      sub: 'owner1',
      userId: 1,
      email: 'owner@shop.vn',
      roles: 'ROLE_USER,ROLE_OWNER',
      storeId: 10,
      storeRole: 'OWNER',
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    localStorage.setItem('accessToken', token);
    localStorage.setItem('refreshToken', 'refresh-token');

    const service = TestBed.inject(AuthService);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.currentUser()?.username).toBe('owner1');
    expect(service.currentUser()?.storeId).toBe(10);
    expect(service.currentUser()?.roles).toEqual(['ROLE_USER', 'ROLE_OWNER']);
  });

  it('treats an expired token in localStorage as unauthenticated', () => {
    const token = fakeToken({
      sub: 'owner1',
      userId: 1,
      email: 'owner@shop.vn',
      roles: 'ROLE_USER',
      exp: Math.floor(Date.now() / 1000) - 3600,
    });
    localStorage.setItem('accessToken', token);

    const service = TestBed.inject(AuthService);

    expect(service.isAuthenticated()).toBe(false);
  });

  it('treats a corrupt token string as unauthenticated without throwing', () => {
    localStorage.setItem('accessToken', 'not-a-valid-jwt');

    expect(() => TestBed.inject(AuthService)).not.toThrow();
    const service = TestBed.inject(AuthService);
    expect(service.isAuthenticated()).toBe(false);
  });

  it('handles a token with no storeId/storeRole (plain customer)', () => {
    const token = fakeToken({
      sub: 'customer1',
      userId: 2,
      email: 'customer@example.com',
      roles: 'ROLE_USER',
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    localStorage.setItem('accessToken', token);

    const service = TestBed.inject(AuthService);

    expect(service.currentUser()?.storeId).toBeUndefined();
    expect(service.currentUser()?.storeRole).toBeUndefined();
  });

  it('login() posts to /auth/login and persists tokens on success', () => {
    const service = TestBed.inject(AuthService);
    const token = fakeToken({
      sub: 'owner1',
      userId: 1,
      email: 'owner@shop.vn',
      roles: 'ROLE_USER,ROLE_OWNER',
      exp: Math.floor(Date.now() / 1000) + 3600,
    });

    service.login({ username: 'owner1', password: 'Secret@123' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'owner1', password: 'Secret@123' });
    req.flush({
      accessToken: token,
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      id: 1,
      username: 'owner1',
      email: 'owner@shop.vn',
      roles: ['ROLE_USER', 'ROLE_OWNER'],
    });

    expect(service.isAuthenticated()).toBe(true);
    expect(localStorage.getItem('accessToken')).toBe(token);
    expect(localStorage.getItem('refreshToken')).toBe('refresh-token');
  });

  it('login() failure leaves state unauthenticated and localStorage untouched', () => {
    const service = TestBed.inject(AuthService);
    let errored = false;

    service.login({ username: 'owner1', password: 'wrong' }).subscribe({
      error: () => {
        errored = true;
      },
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    req.flush({ error: 'Invalid username or password' }, { status: 401, statusText: 'Unauthorized' });

    expect(errored).toBe(true);
    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('accessToken')).toBeNull();
  });

  it('logout() clears signals and localStorage', () => {
    const token = fakeToken({
      sub: 'owner1',
      userId: 1,
      email: 'owner@shop.vn',
      roles: 'ROLE_USER',
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    localStorage.setItem('accessToken', token);
    localStorage.setItem('refreshToken', 'refresh-token');
    const service = TestBed.inject(AuthService);
    expect(service.isAuthenticated()).toBe(true);

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
  });

  it('verifyOtp() posts to /auth/verify-otp', () => {
    const service = TestBed.inject(AuthService);

    service.verifyOtp({ email: 'owner@shop.vn', otpCode: '123456' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/verify-otp`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'owner@shop.vn', otpCode: '123456' });
    req.flush({ message: 'Account verified successfully. You can now login.' });
  });

  it('refresh() posts the stored refreshToken and persists the new response', () => {
    localStorage.setItem('refreshToken', 'old-refresh-token');
    const service = TestBed.inject(AuthService);
    const newAccessToken = fakeToken({
      sub: 'owner1',
      userId: 1,
      email: 'owner@shop.vn',
      roles: 'ROLE_USER',
      exp: Math.floor(Date.now() / 1000) + 3600,
    });

    service.refresh().subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    expect(req.request.body).toEqual({ refreshToken: 'old-refresh-token' });
    req.flush({
      accessToken: newAccessToken,
      refreshToken: 'old-refresh-token',
      tokenType: 'Bearer',
      id: 1,
      username: 'owner1',
      email: 'owner@shop.vn',
      roles: ['ROLE_USER'],
    });

    expect(localStorage.getItem('accessToken')).toBe(newAccessToken);
  });
});
