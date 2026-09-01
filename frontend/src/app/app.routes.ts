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
  {
    path: 'store/:storeSlug',
    loadComponent: () => import('./features/storefront/home/storefront-home').then((m) => m.StorefrontHome),
  },
  {
    path: 'store/:storeSlug/products/:productId',
    loadComponent: () =>
      import('./features/storefront/product-detail/storefront-product-detail').then(
        (m) => m.StorefrontProductDetail,
      ),
  },
  {
    path: 'store/:storeSlug/cart',
    canActivate: [authGuard],
    loadComponent: () => import('./features/storefront/cart/storefront-cart').then((m) => m.StorefrontCart),
  },
  {
    path: 'store/:storeSlug/checkout',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/storefront/checkout/storefront-checkout').then((m) => m.StorefrontCheckout),
  },
  {
    path: 'payment/success',
    canActivate: [authGuard],
    loadComponent: () => import('./features/storefront/payment-success/payment-success').then((m) => m.PaymentSuccess),
  },
  {
    path: 'payment/cancel',
    canActivate: [authGuard],
    loadComponent: () => import('./features/storefront/payment-cancel/payment-cancel').then((m) => m.PaymentCancel),
  },
];
