import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './header.html',
})
export class Header {
  private readonly authService = inject(AuthService);
  private readonly cartService = inject(CartService);
  private readonly router = inject(Router);

  readonly currentUser = this.authService.currentUser;
  readonly cartItemCount = this.cartService.itemCount;
  readonly activeStoreSlug = this.cartService.activeStoreSlug;

  logout(): void {
    this.authService.logout();
    this.router.navigateByUrl('/');
  }
}
