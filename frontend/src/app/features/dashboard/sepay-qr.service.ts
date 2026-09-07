import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { EMPTY, Observable, catchError, switchMap, take, takeWhile, timer } from 'rxjs';

import { environment } from '../../../environments/environment';

export type PosQrStatus = 'PENDING' | 'PAID';

/** One counter QR, from the moment the cashier shows it until SePay's webhook says the transfer landed. Mirrors PosQrSessionResponse. */
export interface PosQrSession {
  id: number;
  /** The transfer content the QR pre-fills - shown under it so a customer whose bank app drops it can type it back in. */
  reference: string;
  amount: number;
  qrUrl: string;
  status: PosQrStatus;
  /** What actually arrived. Below `amount` while still PENDING means the customer short-paid. */
  transferredAmount: number | null;
  paidAt: string | null;
  expiresInSeconds: number;
}

const POLL_INTERVAL_MS = 3000;

@Injectable({ providedIn: 'root' })
export class SepayQrService {
  constructor(private readonly http: HttpClient) {}

  /**
   * Opens a session and returns the QR to show. Unlike a plain VietQR image,
   * this one carries a reference the SePay webhook can match an incoming
   * transfer against - which is what lets one single bank account tell every
   * counter payment apart, from each other and from storefront orders.
   */
  createSession(amount: number): Observable<PosQrSession> {
    return this.http.post<PosQrSession>(`${environment.apiUrl}/payments/pos/qr`, { amount });
  }

  getSession(id: number): Observable<PosQrSession> {
    return this.http.get<PosQrSession>(`${environment.apiUrl}/payments/pos/qr/${id}`);
  }

  /**
   * Polls one session until the money lands or the server's window closes,
   * then completes - the last value it emitted stays on screen either way.
   * A failed poll ends the watch rather than erroring out, so a network blip
   * leaves the cashier looking at a QR and their banking app, not a broken
   * screen.
   *
   * The number of polls is counted off the server's own remaining-seconds
   * instead of a deadline: the two clocks are in different zones, and a
   * browser in UTC+7 comparing against a UTC timestamp would decide the
   * session had already expired.
   */
  watchSession(session: PosQrSession): Observable<PosQrSession> {
    const polls = Math.max(1, Math.ceil((session.expiresInSeconds * 1000) / POLL_INTERVAL_MS));
    return timer(POLL_INTERVAL_MS, POLL_INTERVAL_MS).pipe(
      take(polls),
      switchMap(() => this.getSession(session.id)),
      takeWhile((s) => s.status === 'PENDING', true),
      catchError(() => EMPTY),
    );
  }
}
