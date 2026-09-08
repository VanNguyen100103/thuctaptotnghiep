import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

/** Which flow started the redirect. Zalo sends the browser back the same way for both, so the page has to remember. */
export type ZaloIntent = 'login' | 'link';

const INTENT_KEY = 'zaloIntent';

export interface ZaloLinkStatus {
  linked: boolean;
  available: boolean;
}

/**
 * Zalo Login v4, the redirect half.
 *
 * Zalo offers no popup flow for the web, so the browser leaves the app,
 * approves at Zalo, and comes back to /auth/zalo/callback with a one-time
 * code. The intent is kept in sessionStorage across that trip because the
 * code alone does not say whether the user meant to sign in or to attach
 * their Zalo account to an account they are already signed into.
 *
 * sessionStorage rather than localStorage on purpose: an abandoned attempt
 * should not still be waiting in another tab tomorrow.
 */
@Injectable({ providedIn: 'root' })
export class ZaloAuthService {
  private readonly http = inject(HttpClient);

  readonly enabled = environment.zaloEnabled;

  /** Sends the browser to Zalo. Remembers why, so the callback knows what to do with the code. */
  startFlow(intent: ZaloIntent): Observable<{ url: string }> {
    sessionStorage.setItem(INTENT_KEY, intent);
    return this.http.get<{ url: string }>(`${environment.apiUrl}/auth/zalo/authorize-url`);
  }

  /** Reads and clears the intent - a code is single-use, so the reason for it is too. */
  consumeIntent(): ZaloIntent {
    const intent = sessionStorage.getItem(INTENT_KEY);
    sessionStorage.removeItem(INTENT_KEY);
    return intent === 'link' ? 'link' : 'login';
  }

  link(code: string, state: string): Observable<{ message: string; linked: boolean }> {
    return this.http.post<{ message: string; linked: boolean }>(`${environment.apiUrl}/auth/zalo/link`, {
      code,
      state,
    });
  }

  unlink(): Observable<{ message: string; linked: boolean }> {
    return this.http.delete<{ message: string; linked: boolean }>(`${environment.apiUrl}/auth/zalo/link`);
  }

  status(): Observable<ZaloLinkStatus> {
    return this.http.get<ZaloLinkStatus>(`${environment.apiUrl}/auth/zalo/link`);
  }
}
