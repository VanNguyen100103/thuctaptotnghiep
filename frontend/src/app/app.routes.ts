import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { guestGuard } from './core/auth/guest.guard';
import { ownerManagerGuard } from './core/auth/owner-manager.guard';

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
    // No guard: signing in arrives here signed out, linking arrives signed in.
    path: 'auth/zalo/callback',
    loadComponent: () => import('./features/login/zalo-callback').then((m) => m.ZaloCallback),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/dashboard/dashboard-overview').then((m) => m.DashboardOverview),
      },
      {
        path: 'products',
        canActivate: [ownerManagerGuard],
        children: [
          {
            path: '',
            loadComponent: () => import('./features/dashboard/product-list').then((m) => m.ProductList),
            children: [
              {
                path: 'new',
                loadComponent: () => import('./features/dashboard/product-form').then((m) => m.ProductForm),
              },
              {
                path: ':productId/edit',
                loadComponent: () => import('./features/dashboard/product-form').then((m) => m.ProductForm),
              },
            ],
          },
        ],
      },
      {
        path: 'suppliers',
        canActivate: [ownerManagerGuard],
        loadComponent: () => import('./features/dashboard/supplier-list').then((m) => m.SupplierList),
      },
      {
        path: 'account',
        loadComponent: () => import('./features/dashboard/account').then((m) => m.Account),
      },
      {
        // Reachable by URL only: the dashboard nav dropped its "Chính sách"
        // tab to match KiotViet's, but the screen (and the store policies the
        // storefront AI chat answers from) is still here.
        path: 'policies',
        canActivate: [ownerManagerGuard],
        loadComponent: () => import('./features/dashboard/policy-list').then((m) => m.PolicyList),
      },
      {
        path: 'delivery-partners',
        canActivate: [ownerManagerGuard],
        loadComponent: () =>
          import('./features/dashboard/delivery-partners').then((m) => m.DeliveryPartners),
      },
      {
        path: 'purchase-orders',
        canActivate: [ownerManagerGuard],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/dashboard/purchase-order-list').then((m) => m.PurchaseOrderList),
          },
          {
            path: 'new',
            loadComponent: () =>
              import('./features/dashboard/purchase-order-form').then((m) => m.PurchaseOrderForm),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./features/dashboard/purchase-order-form').then((m) => m.PurchaseOrderForm),
          },
        ],
      },
    ],
  },
  {
    // Own full-screen layout (not nested under Dashboard's shell/tabs), same
    // as KiotViet's real "Bán hàng" screen reached via the toolbar button
    // rather than a tab - see PosTerminal's own doc comment.
    path: 'dashboard/pos',
    canActivate: [authGuard, ownerManagerGuard],
    loadComponent: () => import('./features/dashboard/pos-terminal').then((m) => m.PosTerminal),
  },
  {
    // Componentless grouping route: every storefront page shares the /store/:storeSlug
    // prefix, and paramsInheritanceStrategy 'always' (app.config.ts) carries storeSlug
    // down to the children below.
    path: 'store/:storeSlug',
    children: [
      {
        path: '',
        loadComponent: () => import('./features/storefront/home/storefront-home').then((m) => m.StorefrontHome),
      },
      {
        path: 'products/:productId',
        loadComponent: () =>
          import('./features/storefront/product-detail/storefront-product-detail').then(
            (m) => m.StorefrontProductDetail,
          ),
      },
      {
        path: 'cart',
        canActivate: [authGuard],
        loadComponent: () => import('./features/storefront/cart/storefront-cart').then((m) => m.StorefrontCart),
      },
      {
        path: 'checkout',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/storefront/checkout/storefront-checkout').then((m) => m.StorefrontCheckout),
      },
    ],
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
