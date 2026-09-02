export interface LoginRequest {
  username: string;
  password: string;
}

/** Response body of POST /auth/login and POST /auth/refresh. */
export interface JwtResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  id: number;
  username: string;
  email: string;
  roles: string[];
}

/** Raw decoded JWT payload. roles is comma-joined here, unlike JwtResponse.roles. */
export interface JwtPayload {
  sub: string;
  userId: number;
  email: string;
  roles: string;
  storeId?: number;
  storeRole?: 'OWNER' | 'MANAGER' | 'STAFF' | 'SUPER_ADMIN';
  iat: number;
  exp: number;
}

/** App-facing shape derived from the decoded token - the single source of truth. */
export interface AuthUser {
  id: number;
  username: string;
  email: string;
  roles: string[];
  storeId?: number;
  storeRole?: 'OWNER' | 'MANAGER' | 'STAFF' | 'SUPER_ADMIN';
}

export interface RegisterStoreRequest {
  storeName: string;
  storeSlug: string;
  storePhone?: string;
  storeAddress?: string;
  storeIndustry?: string;
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
}

export interface RegisterStoreResponse {
  message: string;
  storeSlug: string;
  storeName: string;
  username: string;
  trialDays: number;
}

export interface VerifyOtpRequest {
  email: string;
  otpCode: string;
}
