import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { guestGuard } from './core/auth/guest.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/landing/landing').then((m) => m.Landing),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/store-register/store-register').then((m) => m.StoreRegister),
  },
  {
    path: 'verify-otp',
    loadComponent: () => import('./features/verify-otp/verify-otp').then((m) => m.VerifyOtp),
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
  },
];
