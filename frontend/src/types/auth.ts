/**
 * Authentication types
 * Synchronized with Spring Boot backend API responses
 */

export enum UserRole {
  ADMIN = 'ROLE_ADMIN',
  USER = 'ROLE_USER',
  CUSTOMER = 'ROLE_CUSTOMER',
}

export interface User {
  id: number;
  username: string;
  email: string;
  fullName: string;
  role: UserRole;
  phoneNumber?: string;
  address?: string;
  avatarUrl?: string;
  enabled: boolean;
  twoFactorEnabled: boolean;
}

export interface LoginRequest {
  username: string;
  password: string;
  totpCode?: string;
}

/**
 * Backend JwtResponse format from Spring Boot
 * Matches: com.ut.edu.backend.dto.JwtResponse
 * Example response from POST /api/auth/login:
 * {
 *   "accessToken": "eyJhbGc...",
 *   "refreshToken": "eyJhbGc...",
 *   "tokenType": "Bearer",
 *   "id": 3,
 *   "username": "nganhvan",
 *   "email": "naveivaner@gmail.com",
 *   "roles": ["ROLE_USER", "ROLE_ADMIN"]
 * }
 */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  id: number;
  username: string;
  email: string;
  roles: string[]; // Array of role strings like ["ROLE_USER", "ROLE_ADMIN"]
  avatarUrl?: string; // Avatar URL if available
}

/**
 * Token refresh request
 */
export interface RefreshTokenRequest {
  refreshToken: string;
}

/**
 * Token refresh response (same format as login)
 */
export interface RefreshTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  id: number;
  username: string;
  email: string;
  roles: string[];
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
}

/**
 * JWT Token Payload (decoded from accessToken)
 */
export interface JwtPayload {
  sub: string; // username
  userId: number;
  email: string;
  username: string;
  fullName?: string;
  roles: string | string[]; // Can be comma-separated string or array
  iat: number; // Issued at timestamp
  exp: number; // Expiration timestamp
}
