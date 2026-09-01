import { JwtPayload } from './auth.models';

/**
 * Decodes a JWT payload client-side (no signature verification - only the
 * server does that; this is purely for reading claims to drive UI state).
 * Returns null instead of throwing on any malformed input.
 */
export function decodeJwtPayload<T = JwtPayload>(token: string): T | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    const json = new TextDecoder('utf-8').decode(bytes);
    return JSON.parse(json) as T;
  } catch {
    return null;
  }
}

/** Fail-safe: a missing or unparseable exp claim counts as expired. */
export function isTokenExpired(payload: JwtPayload | null): boolean {
  if (!payload || typeof payload.exp !== 'number') {
    return true;
  }
  return Date.now() >= payload.exp * 1000;
}
