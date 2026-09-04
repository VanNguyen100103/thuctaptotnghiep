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
