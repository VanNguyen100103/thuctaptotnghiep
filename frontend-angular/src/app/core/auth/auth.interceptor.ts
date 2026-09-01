import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const isApiRequest = req.url.startsWith(environment.apiUrl);
  const token = authService.accessToken();

  const authorizedReq =
    isApiRequest && token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authorizedReq).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && authService.isAuthenticated()) {
        // Session died server-side (expired/revoked token) - a failed login
        // attempt is also a 401 but isAuthenticated() is already false then,
        // so this doesn't fire for that case.
        authService.logout();
        router.navigateByUrl('/login');
      }
      return throwError(() => err);
    }),
  );
};
