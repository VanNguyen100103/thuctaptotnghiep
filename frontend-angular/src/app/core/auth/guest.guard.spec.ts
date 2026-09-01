import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';

import { guestGuard } from './guest.guard';
import { AuthService } from './auth.service';

function runGuard() {
  const route: any = {};
  const state: any = {};
  return TestBed.runInInjectionContext(() => guestGuard(route, state));
}

describe('guestGuard', () => {
  it('allows navigation when not authenticated', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { isAuthenticated: () => false } }],
    });

    expect(runGuard()).toBe(true);
  });

  it('redirects an authenticated user to /dashboard', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { isAuthenticated: () => true } }],
    });
    const router = TestBed.inject(Router);
    const createUrlTreeSpy = vi.spyOn(router, 'createUrlTree');

    const result = runGuard();

    expect(result).toBeInstanceOf(UrlTree);
    expect(createUrlTreeSpy).toHaveBeenCalledWith(['/dashboard']);
  });
});
