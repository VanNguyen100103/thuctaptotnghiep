import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterOutlet } from '@angular/router';

import { ChatWidget } from './chat/chat-widget';

/**
 * Thin shared shell for every store/:storeSlug page - the single mount point
 * for the AI chat widget, so it appears on the whole storefront (home,
 * product detail, cart, checkout) without being duplicated into each page.
 * Requires paramsInheritanceStrategy: 'always' in app.config.ts so storeSlug
 * still reaches child routes with a non-empty path (see app.routes.ts).
 */
@Component({
  selector: 'app-storefront-layout',
  standalone: true,
  imports: [RouterOutlet, ChatWidget],
  templateUrl: './storefront-layout.html',
})
export class StorefrontLayout {
  private readonly route = inject(ActivatedRoute);

  private readonly paramMap = toSignal(this.route.paramMap, { requireSync: true });
  readonly storeSlug = computed(() => this.paramMap()!.get('storeSlug')!);
}
