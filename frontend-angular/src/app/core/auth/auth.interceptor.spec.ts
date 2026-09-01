import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { environment } from '../../../environments/environment';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let logoutSpy: ReturnType<typeof vi.fn>;
  let isAuthenticated: boolean;

  function setup(accessToken: string | null, authenticated: boolean) {
    isAuthenticated = authenticated;
    logoutSpy = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            accessToken: () => accessToken,
            isAuthenticated: () => isAuthenticated,
            logout: logoutSpy,
          },
        },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('attaches the Authorization header for API requests when a token is present', () => {
    setup('token-123', true);

    http.get(`${environment.apiUrl}/store/subscription`).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/store/subscription`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-123');
    req.flush({});
  });

  it('does not attach the header when there is no token', () => {
    setup(null, false);

    http.get(`${environment.apiUrl}/store/subscription`).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/store/subscription`);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('does not attach the header for a non-API URL', () => {
    setup('token-123', true);

    http.get('https://example.com/other').subscribe();

    const req = httpMock.expectOne('https://example.com/other');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('logs out and redirects on a 401 while the user was authenticated', () => {
    setup('token-123', true);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    http.get(`${environment.apiUrl}/store/subscription`).subscribe({ error: () => {} });

    const req = httpMock.expectOne(`${environment.apiUrl}/store/subscription`);
    req.flush({ error: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(logoutSpy).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });

  it('does not log out or redirect on a 401 while not authenticated (e.g. failed login)', () => {
    setup(null, false);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    http.post(`${environment.apiUrl}/auth/login`, {}).subscribe({ error: () => {} });

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    req.flush({ error: 'Invalid username or password' }, { status: 401, statusText: 'Unauthorized' });

    expect(logoutSpy).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
