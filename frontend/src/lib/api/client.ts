/**
 * API Client Configuration
 * Handles HTTP requests to Spring Boot backend with JWT authentication
 */

// Use empty string for relative paths when using nginx proxy
// This allows nginx to proxy /api/* to backend without CORS issues
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || '';

console.log('API_BASE_URL configured:', API_BASE_URL || '(relative paths)');

export interface ApiError {
  message: string;
  status: number;
  details?: any;
}

/**
 * Get JWT access token from localStorage (client-side only)
 */
export const getToken = (): string | null => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('accessToken');
};

/**
 * Save JWT access token to localStorage
 */
export const setToken = (token: string): void => {
  if (typeof window !== 'undefined') {
    localStorage.setItem('accessToken', token);
  }
};

/**
 * Get JWT refresh token from localStorage
 */
export const getRefreshToken = (): string | null => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('refreshToken');
};

/**
 * Save JWT refresh token to localStorage
 */
export const setRefreshToken = (token: string): void => {
  if (typeof window !== 'undefined') {
    localStorage.setItem('refreshToken', token);
  }
};

/**
 * Remove JWT tokens and CSRF token from localStorage
 */
export const removeToken = (): void => {
  if (typeof window !== 'undefined') {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('csrfToken'); // Also remove CSRF token on logout
  }
};

/**
 * Get CSRF token from localStorage (fallback to cookie)
 * Since cookies don't work reliably across origins, we store in localStorage
 */
export const getCsrfToken = (): string | null => {
  if (typeof window === 'undefined') return null;

  // Try localStorage first
  const storedToken = localStorage.getItem('csrfToken');
  if (storedToken) return storedToken;

  // Fallback to cookie
  const cookies = document.cookie.split(';');
  for (const cookie of cookies) {
    const [name, value] = cookie.trim().split('=');
    if (name === 'XSRF-TOKEN') {
      return decodeURIComponent(value);
    }
  }
  return null;
};

/**
 * Save CSRF token to localStorage
 */
export const setCsrfToken = (token: string): void => {
  if (typeof window !== 'undefined') {
    localStorage.setItem('csrfToken', token);
  }
};

/**
 * Remove CSRF token from localStorage
 */
export const removeCsrfToken = (): void => {
  if (typeof window !== 'undefined') {
    localStorage.removeItem('csrfToken');
  }
};

/**
 * Fetch new CSRF token from backend
 */
export async function fetchCsrfToken(): Promise<string | null> {
  try {
    console.log('[CSRF] Fetching new CSRF token...');

    const jwtToken = getToken();
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };

    // Add JWT token if available
    if (jwtToken) {
      headers['Authorization'] = `Bearer ${jwtToken}`;
    }

    const response = await fetch(`${API_BASE_URL}/api/auth/csrf-token`, {
      method: 'GET',
      credentials: 'include', // Important: include cookies
      headers,
    });

    if (response.ok) {
      const data = await response.json();
      console.log('[CSRF] Response:', data);

      const newToken = data.token;
      if (newToken) {
        // Save to localStorage since cookies don't work reliably
        setCsrfToken(newToken);
        console.log('[CSRF] Token saved to localStorage:', newToken.substring(0, 20) + '...');
        return newToken;
      } else {
        console.error('[CSRF] No token in response');
      }
    } else {
      console.error('[CSRF] Failed to fetch token, status:', response.status);
    }
  } catch (error) {
    console.error('[CSRF] Failed to fetch CSRF token:', error);
  }
  return null;
}

/**
 * Generic API request function with JWT authentication and CSRF protection
 */
export async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {},
  retryWithCsrf: boolean = true
): Promise<T> {
  const token = getToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  // Add Authorization header if token exists
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  // Add CSRF token for state-changing requests (POST, PUT, PATCH, DELETE)
  const method = options.method?.toUpperCase() || 'GET';
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    // ALWAYS fetch a fresh token before each state-changing request
    // Since CSRF tokens are one-time use only
    console.log('[CSRF] Fetching fresh token for', method, 'request...');
    const csrfToken = await fetchCsrfToken();

    if (csrfToken) {
      headers['X-XSRF-TOKEN'] = csrfToken;
      console.log('[CSRF] Using fresh token:', csrfToken.substring(0, 20) + '...');
    } else {
      console.error('[CSRF] Failed to get CSRF token for request');
    }
  }

  const config: RequestInit = {
    ...options,
    headers,
    credentials: 'include', // Important: include cookies for CSRF token
  };

  try {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, config);

    // Handle CSRF token missing/invalid (403 Forbidden)
    if (response.status === 403 && retryWithCsrf) {
      console.log('[CSRF] Token invalid or missing (403), fetching new token...');

      // Fetch new CSRF token
      const newCsrfToken = await fetchCsrfToken();

      if (newCsrfToken) {
        console.log('[CSRF] Retrying request with new CSRF token');
        // Retry request with new CSRF token (prevent infinite loop with retryWithCsrf=false)
        return apiRequest<T>(endpoint, options, false);
      }
    }

    // Handle non-OK responses
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      const error: ApiError = {
        message: errorData.message || `HTTP ${response.status}: ${response.statusText}`,
        status: response.status,
        details: errorData,
      };
      throw error;
    }

    // Handle 204 No Content
    if (response.status === 204) {
      return {} as T;
    }

    return await response.json();
  } catch (error: any) {
    // Network errors or JSON parsing errors
    if (error.status) {
      throw error; // Already formatted ApiError
    }

    throw {
      message: error.message || 'Network error occurred',
      status: 0,
      details: error,
    } as ApiError;
  }
}

/**
 * GET request
 */
export async function get<T>(endpoint: string, options?: RequestInit): Promise<T> {
  return apiRequest<T>(endpoint, { ...options, method: 'GET' });
}

/**
 * POST request
 */
export async function post<T>(
  endpoint: string,
  data?: any,
  options?: RequestInit
): Promise<T> {
  return apiRequest<T>(endpoint, {
    ...options,
    method: 'POST',
    body: data ? JSON.stringify(data) : undefined,
  });
}

/**
 * PUT request
 */
export async function put<T>(
  endpoint: string,
  data?: any,
  options?: RequestInit
): Promise<T> {
  return apiRequest<T>(endpoint, {
    ...options,
    method: 'PUT',
    body: data ? JSON.stringify(data) : undefined,
  });
}

/**
 * PATCH request
 */
export async function patch<T>(
  endpoint: string,
  data?: any,
  options?: RequestInit
): Promise<T> {
  return apiRequest<T>(endpoint, {
    ...options,
    method: 'PATCH',
    body: data ? JSON.stringify(data) : undefined,
  });
}

/**
 * DELETE request
 */
export async function del<T>(
  endpoint: string,
  data?: any,
  options?: RequestInit
): Promise<T> {
  return apiRequest<T>(endpoint, {
    ...options,
    method: 'DELETE',
    body: data ? JSON.stringify(data) : undefined,
  });
}
