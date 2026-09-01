import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';

import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

function runGuard(url: string) {
  const route: any = {};
  const state: any = { url };
  return TestBed.runInInjectionContext(() => authGuard(route, state));
}

describe('authGuard', () => {
  it('allows navigation when authenticated', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { isAuthenticated: () => true } }],
    });

    expect(runGuard('/dashboard')).toBe(true);
  });

  it('redirects to /login with returnUrl when not authenticated', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { isAuthenticated: () => false } }],
    });
    const router = TestBed.inject(Router);
    const createUrlTreeSpy = vi.spyOn(router, 'createUrlTree');

    const result = runGuard('/dashboard');

    expect(result).toBeInstanceOf(UrlTree);
    expect(createUrlTreeSpy).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/dashboard' },
    });
  });
});
