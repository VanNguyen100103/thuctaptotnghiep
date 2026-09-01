import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { StoreProfile } from './store-profile.models';

/**
 * GET /store resolves the logged-in owner/manager's own store (slug
 * included) from their JWT's tenant - the JWT itself only carries a numeric
 * storeId, no slug, so this is how the frontend finds its way to
 * /store/{slug} (the public storefront) without the user typing it in.
 */
@Injectable({ providedIn: 'root' })
export class StoreProfileService {
  constructor(private readonly http: HttpClient) {}

  getCurrentStore(): Observable<StoreProfile> {
    return this.http.get<StoreProfile>(`${environment.apiUrl}/store`);
  }
}
