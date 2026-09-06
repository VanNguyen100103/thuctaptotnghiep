import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withInMemoryScrolling, withRouterConfig } from '@angular/router';

import { authInterceptor } from './core/auth/auth.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // anchorScrolling: needed for the header's "Tính năng"/"Bảng giá"/"Về chúng tôi" fragment links to actually scroll.
    // paramsInheritanceStrategy 'always': store/:storeSlug is a parent route (StorefrontLayout) wrapping
    // child pages with their own non-empty paths (products/:productId, cart, checkout) - the default
    // 'emptyOnly' strategy would stop exposing storeSlug on those children entirely.
    provideRouter(
      routes,
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
      withRouterConfig({ paramsInheritanceStrategy: 'always' }),
    ),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
