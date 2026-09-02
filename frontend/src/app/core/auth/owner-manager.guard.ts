import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Guards routes that need real store-scoped write access (product/order/etc.
 * management) - unlike bare /dashboard, these are new directly-navigable
 * URLs, so they need their own check rather than relying on a parent
 * component's presentational @if.
 */
export const ownerManagerGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const role = authService.currentUser()?.storeRole;
  if (role === 'OWNER' || role === 'MANAGER') {
    return true;
  }

  return router.createUrlTree(['/dashboard']);
};
