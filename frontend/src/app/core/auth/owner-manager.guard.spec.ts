import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';

import { AuthService } from './auth.service';
import { ownerManagerGuard } from './owner-manager.guard';

function runGuard() {
  return TestBed.runInInjectionContext(() => ownerManagerGuard({} as any, {} as any));
}

describe('ownerManagerGuard', () => {
  it('allows navigation for OWNER', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { currentUser: () => ({ storeRole: 'OWNER' }) } }],
    });

    expect(runGuard()).toBe(true);
  });

  it('allows navigation for MANAGER', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { currentUser: () => ({ storeRole: 'MANAGER' }) } }],
    });

    expect(runGuard()).toBe(true);
  });

  it('redirects to /dashboard for STAFF', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { currentUser: () => ({ storeRole: 'STAFF' }) } }],
    });
    const router = TestBed.inject(Router);
    const createUrlTreeSpy = vi.spyOn(router, 'createUrlTree');

    const result = runGuard();

    expect(result).toBeInstanceOf(UrlTree);
    expect(createUrlTreeSpy).toHaveBeenCalledWith(['/dashboard']);
  });

  it('redirects to /dashboard when there is no store role at all (plain customer)', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { currentUser: () => ({}) } }],
    });

    expect(runGuard()).toBeInstanceOf(UrlTree);
  });
});
