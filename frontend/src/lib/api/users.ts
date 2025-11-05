/**
 * User API Client
 * Handles all user profile and management API calls
 */

import { get, put, del, getToken, fetchCsrfToken } from './client';
import type { UserProfile, UpdateProfileRequest, ChangePasswordRequest } from '@/types/user';

// ==================== AUTHENTICATED ENDPOINTS ====================

/**
 * Get current user profile
 * Endpoint: GET /api/users/me
 * Requires: Authentication
 */
export async function getCurrentUser(): Promise<UserProfile> {
  return get<UserProfile>('/api/users/me');
}

/**
 * Update user profile by user ID
 * Endpoint: PUT /api/users/{userId}
 * Requires: Authentication
 */
export async function updateUserProfile(userId: number, data: UpdateProfileRequest): Promise<{ message: string; user: UserProfile }> {
  return put<{ message: string; user: UserProfile }>(`/api/users/${userId}`, data);
}

/**
 * Update user profile (legacy - uses /me endpoint)
 * Endpoint: PUT /api/users/me
 * Requires: Authentication
 */
export async function updateProfile(data: UpdateProfileRequest): Promise<UserProfile> {
  return put<UserProfile>('/api/users/me', data);
}

/**
 * Change password
 * Endpoint: PUT /api/users/me/password
 * Requires: Authentication
 */
export async function changePassword(data: ChangePasswordRequest): Promise<{ message: string }> {
  return put<{ message: string }>('/api/users/me/password', data);
}

/**
 * Enable 2FA
 * Endpoint: POST /api/users/me/2fa/enable
 * Requires: Authentication
 */
export async function enable2FA(): Promise<{
  qrCode: string;
  secret: string;
  backupCodes: string[];
}> {
  return get<{
    qrCode: string;
    secret: string;
    backupCodes: string[];
  }>('/api/users/me/2fa/enable');
}

/**
 * Disable 2FA
 * Endpoint: POST /api/users/me/2fa/disable
 * Requires: Authentication
 */
export async function disable2FA(code: string): Promise<{ message: string }> {
  return put<{ message: string }>('/api/users/me/2fa/disable', { code });
}

/**
 * Get user spending statistics
 * Endpoint: GET /api/users/me/spending
 * Requires: Authentication
 */
export async function getUserSpending(): Promise<{
  totalSpent: number;
  totalOrders: number;
  averageOrderValue: number;
  lastOrderDate?: string;
  monthlySpending: Array<{ month: string; amount: number }>;
}> {
  return get<{
    totalSpent: number;
    totalOrders: number;
    averageOrderValue: number;
    lastOrderDate?: string;
    monthlySpending: Array<{ month: string; amount: number }>;
  }>('/api/users/me/spending');
}

/**
 * Delete/Deactivate user account
 * Endpoint: DELETE /api/users/{userId}
 * Requires: Authentication
 */
export async function deleteUserAccount(userId: number): Promise<{ message: string; userId: number; avatarDeleted: boolean; enabled: boolean }> {
  return del<{ message: string; userId: number; avatarDeleted: boolean; enabled: boolean }>(`/api/users/${userId}`);
}

/**
 * Upload user avatar
 * Endpoint: POST /api/users/{userId}/avatar
 * Requires: Authentication + CSRF Token
 */
export async function uploadAvatar(userId: number, file: File): Promise<{ message: string; avatarUrl: string; user: { id: number; username: string; avatarUrl: string } }> {
  const formData = new FormData();
  formData.append('avatar', file);

  // Get JWT token
  const token = getToken();

  // Get fresh CSRF token for this upload
  console.log('[Upload Avatar] Fetching CSRF token...');
  const csrfToken = await fetchCsrfToken();

  const headers: Record<string, string> = {};

  // Add JWT token
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  // Add CSRF token (required for POST requests)
  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
    console.log('[Upload Avatar] Using CSRF token:', csrfToken.substring(0, 20) + '...');
  } else {
    console.error('[Upload Avatar] No CSRF token available!');
    throw new Error('CSRF token not available');
  }

  const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || '';
  const response = await fetch(`${API_BASE_URL}/api/users/${userId}/avatar`, {
    method: 'POST',
    headers,
    body: formData,
    credentials: 'include', // Important for CSRF cookie
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: 'Failed to upload avatar' }));
    throw new Error(error.error || `Upload failed with status ${response.status}`);
  }

  return response.json();
}

/**
 * Delete user avatar
 * Endpoint: DELETE /api/users/{userId}/avatar
 * Requires: Authentication
 */
export async function deleteAvatar(userId: number): Promise<{ message: string; avatarDeleted: boolean }> {
  return del<{ message: string; avatarDeleted: boolean }>(`/api/users/${userId}/avatar`);
}
