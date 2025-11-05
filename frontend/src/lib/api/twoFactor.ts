/**
 * Two-Factor Authentication API Client
 * Handles all 2FA-related API calls
 */

import { get, post } from './client';

export interface TwoFactorSetupResponse {
  secret: string;
  qrCodeDataUrl: string;
  backupCodes: string[];
}

export interface TwoFactorStatusResponse {
  enabled: boolean;
  username: string;
}

export interface VerifyTotpRequest {
  code: string;
}

/**
 * Enable 2FA - Step 1: Generate QR code and backup codes
 * Endpoint: POST /api/2fa/enable
 */
export async function enableTwoFactor(): Promise<TwoFactorSetupResponse> {
  return post<TwoFactorSetupResponse>('/api/2fa/enable', {});
}

/**
 * Enable 2FA - Step 2: Verify TOTP code and activate
 * Endpoint: POST /api/2fa/verify
 */
export async function verifyAndActivateTwoFactor(code: string): Promise<{ message: string; enabled: boolean }> {
  return post<{ message: string; enabled: boolean }>('/api/2fa/verify', { code });
}

/**
 * Disable 2FA
 * Endpoint: POST /api/2fa/disable
 */
export async function disableTwoFactor(code: string): Promise<{ message: string; enabled: boolean }> {
  return post<{ message: string; enabled: boolean }>('/api/2fa/disable', { code });
}

/**
 * Regenerate backup codes
 * Endpoint: POST /api/2fa/backup-codes/regenerate
 */
export async function regenerateBackupCodes(code: string): Promise<{ message: string; backupCodes: string[] }> {
  return post<{ message: string; backupCodes: string[] }>('/api/2fa/backup-codes/regenerate', { code });
}

/**
 * Check 2FA status
 * Endpoint: GET /api/2fa/status
 */
export async function getTwoFactorStatus(): Promise<TwoFactorStatusResponse> {
  return get<TwoFactorStatusResponse>('/api/2fa/status');
}
