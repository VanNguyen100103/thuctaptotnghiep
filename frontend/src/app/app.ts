import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { Header } from './layout/header/header';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly router = inject(Router);

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /**
   * The whole /dashboard tree carries its own KiotViet-style blue chrome
   * (DashboardHeader, and PosTerminal's own full-screen bar under
   * /dashboard/pos), so the white marketing header must not double up above
   * it. Everything the marketing header offered a signed-in user - account,
   * storefront, sign-out - lives in DashboardHeader's avatar menu instead.
   */
  readonly showHeader = computed(() => !this.currentUrl().startsWith('/dashboard'));
}
