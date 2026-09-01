import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AuthUser,
  JwtPayload,
  JwtResponse,
  LoginRequest,
  VerifyOtpRequest,
} from './auth.models';
import { decodeJwtPayload, isTokenExpired } from './jwt.util';

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

function toAuthUser(payload: JwtPayload): AuthUser {
  return {
    id: payload.userId,
    username: payload.sub,
    email: payload.email,
    roles: payload.roles ? payload.roles.split(',') : [],
    storeId: payload.storeId,
    storeRole: payload.storeRole,
  };
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly accessTokenSignal = signal<string | null>(null);
  private readonly refreshTokenSignal = signal<string | null>(null);

  readonly accessToken = this.accessTokenSignal.asReadonly();

  readonly currentUser = computed<AuthUser | null>(() => {
    const token = this.accessTokenSignal();
    if (!token) {
      return null;
    }
    const payload = decodeJwtPayload(token);
    if (!payload || isTokenExpired(payload)) {
      return null;
    }
    return toAuthUser(payload);
  });

  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  constructor(private readonly http: HttpClient) {
    this.rehydrate();
  }

  private rehydrate(): void {
    const accessToken = localStorage.getItem(ACCESS_TOKEN_KEY);
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (accessToken) {
      this.accessTokenSignal.set(accessToken);
    }
    if (refreshToken) {
      this.refreshTokenSignal.set(refreshToken);
    }
  }

  private persist(response: JwtResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
    this.accessTokenSignal.set(response.accessToken);
    this.refreshTokenSignal.set(response.refreshToken);
  }

  login(request: LoginRequest): Observable<JwtResponse> {
    return this.http
      .post<JwtResponse>(`${environment.apiUrl}/auth/login`, request)
      .pipe(tap((response) => this.persist(response)));
  }

  verifyOtp(request: VerifyOtpRequest): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${environment.apiUrl}/auth/verify-otp`, request);
  }

  /**
   * Renews the access token. Not called automatically anywhere yet (no
   * silent-refresh-on-401 in this slice - that needs request-queuing to do
   * properly and is a deliberate scope boundary for a later pass).
   */
  refresh(): Observable<JwtResponse> {
    const refreshToken = this.refreshTokenSignal();
    return this.http
      .post<JwtResponse>(`${environment.apiUrl}/auth/refresh`, { refreshToken })
      .pipe(tap((response) => this.persist(response)));
  }

  logout(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.accessTokenSignal.set(null);
    this.refreshTokenSignal.set(null);
  }
}
