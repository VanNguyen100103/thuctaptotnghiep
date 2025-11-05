/**
 * Authentication API functions
 * Handles login, register, logout, token refresh
 */

import { post } from './client';
import {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RefreshTokenResponse,
  JwtPayload
} from '@/types/auth';
import { setToken, setRefreshToken, removeToken, getRefreshToken } from './client';

/**
 * Login user
 * POST /api/auth/login
 * Backend returns: { accessToken, refreshToken, tokenType, id, username, email, roles }
 */
export async function login(credentials: LoginRequest): Promise<LoginResponse> {
  const response = await post<LoginResponse>('/api/auth/login', credentials);

  // Save both tokens to localStorage
  if (response.accessToken) {
    setToken(response.accessToken);
    console.log('[auth.ts] Access token saved to localStorage');
  }

  if (response.refreshToken) {
    setRefreshToken(response.refreshToken);
    console.log('[auth.ts] Refresh token saved to localStorage');
  }

  console.log('[auth.ts] Login successful:', {
    id: response.id,
    username: response.username,
    email: response.email,
    roles: response.roles,
  });

  return response;
}

/**
 * Register new user
 * POST /api/auth/register
 * Backend returns: { accessToken, refreshToken, tokenType, id, username, email, roles }
 */
export async function register(data: RegisterRequest): Promise<LoginResponse> {
  const response = await post<LoginResponse>('/api/auth/register', data);

  // Save both tokens to localStorage
  if (response.accessToken) {
    setToken(response.accessToken);
  }

  if (response.refreshToken) {
    setRefreshToken(response.refreshToken);
  }

  return response;
}

/**
 * Refresh access token using refresh token
 * POST /api/auth/refresh
 * Backend expects: { refreshToken }
 * Backend returns: { accessToken, refreshToken, tokenType, id, username, email, roles }
 */
export async function refreshToken(): Promise<RefreshTokenResponse | null> {
  try {
    const currentRefreshToken = getRefreshToken();

    if (!currentRefreshToken) {
      console.error('[auth.ts] No refresh token found');
      return null;
    }

    const response = await post<RefreshTokenResponse>('/api/auth/refresh', {
      refreshToken: currentRefreshToken,
    });

    // Update tokens in localStorage
    if (response.accessToken) {
      setToken(response.accessToken);
      console.log('[auth.ts] Access token refreshed successfully');
    }

    if (response.refreshToken) {
      setRefreshToken(response.refreshToken);
      console.log('[auth.ts] Refresh token updated');
    }

    return response;
  } catch (error) {
    console.error('[auth.ts] Token refresh failed:', error);
    // Clear tokens if refresh fails
    removeToken();
    return null;
  }
}

/**
 * Logout user
 * Clears tokens and redirects to homepage
 */
export function logout(): void {
  console.log('[auth.ts] Logging out...');
  removeToken();

  // Redirect to homepage after a small delay to ensure state is cleared
  if (typeof window !== 'undefined') {
    setTimeout(() => {
      window.location.href = '/';
    }, 100);
  }
}

/**
 * Decode JWT token to get payload
 * Returns JwtPayload with user info (sub, userId, email, username, roles, iat, exp)
 */
export function decodeToken(token: string): JwtPayload | null {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload) as JwtPayload;
  } catch (error) {
    console.error('[auth.ts] Failed to decode token:', error);
    return null;
  }
}

/**
 * Check if token is expired
 * Returns true if token is expired or invalid
 */
export function isTokenExpired(token: string): boolean {
  const decoded = decodeToken(token);
  if (!decoded || !decoded.exp) return true;

  const currentTime = Date.now() / 1000;
  const isExpired = decoded.exp < currentTime;

  if (isExpired) {
    console.log('[auth.ts] Token is expired');
  }

  return isExpired;
}

/**
 * Check if username is available
 * GET /api/auth/check-username?username=xxx
 */
export async function checkUsernameAvailability(username: string): Promise<{ available: boolean; message: string }> {
  const response = await fetch(`${process.env.NEXT_PUBLIC_API_URL || 'http://localhost'}/api/auth/check-username?username=${encodeURIComponent(username)}`);
  return response.json();
}

/**
 * Check if email is available
 * GET /api/auth/check-email?email=xxx
 */
export async function checkEmailAvailability(email: string): Promise<{ available: boolean; message: string }> {
  const response = await fetch(`${process.env.NEXT_PUBLIC_API_URL || 'http://localhost'}/api/auth/check-email?email=${encodeURIComponent(email)}`);
  return response.json();
}

/**
 * Verify OTP code for registration
 * POST /api/auth/verify-otp
 */
export async function verifyOtp(email: string, otpCode: string): Promise<{ message: string }> {
  return post<{ message: string }>('/api/auth/verify-otp', { email, otpCode });
}

/**
 * Request password reset
 * POST /api/auth/forgot-password
 */
export async function forgotPassword(email: string): Promise<{ message: string }> {
  return post<{ message: string }>('/api/auth/forgot-password', { email });
}

/**
 * Reset password with token
 * POST /api/auth/reset-password
 */
export async function resetPassword(token: string, newPassword: string): Promise<{ message: string }> {
  return post<{ message: string }>('/api/auth/reset-password', { token, newPassword });
}
