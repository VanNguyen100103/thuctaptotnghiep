'use client';

/**
 * Authentication Context
 * Provides authentication state and methods throughout the app
 */

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { User, UserRole, LoginRequest, LoginResponse } from '@/types/auth';
import { login as apiLogin, logout as apiLogout, decodeToken, isTokenExpired } from '@/lib/api/auth';
import { getToken } from '@/lib/api/client';
import { getCurrentUser } from '@/lib/api/users';
import { migrateViewsOnLogin } from '@/lib/api/view-history';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  loading: boolean;
  login: (credentials: LoginRequest) => Promise<LoginResponse>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setTokenState] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Initialize auth state from localStorage on mount
  useEffect(() => {
    const initAuth = async () => {
      const storedToken = getToken();
      console.log('[AuthContext] Initializing - storedToken:', storedToken ? 'exists' : 'null');

      if (storedToken && !isTokenExpired(storedToken)) {
        const decoded = decodeToken(storedToken);
        console.log('[AuthContext] Decoded token:', decoded);

        if (decoded) {
          setTokenState(storedToken);

          // Parse roles from JWT payload
          // Backend JWT contains roles as string (comma-separated) or array
          // Example: "ROLE_USER,ROLE_ADMIN" or ["ROLE_USER", "ROLE_ADMIN"]
          let roles: string[] = [];
          if (typeof decoded.roles === 'string') {
            roles = decoded.roles.split(',').map((r: string) => r.trim());
          } else if (Array.isArray(decoded.roles)) {
            roles = decoded.roles;
          }

          console.log('[AuthContext] Parsed roles:', roles);
          const hasAdminRole = roles.includes('ROLE_ADMIN');
          const role = hasAdminRole ? UserRole.ADMIN : UserRole.USER;

          // Set basic user info first from token
          setUser({
            id: decoded.userId || (typeof decoded.sub === 'number' ? decoded.sub : 0),
            username: decoded.username || decoded.sub,
            email: decoded.email || '',
            fullName: decoded.fullName || decoded.username || '',
            role: role,
            enabled: true,
            twoFactorEnabled: false,
          });

          console.log('[AuthContext] User initialized with role:', role);

          // Fetch full profile in background to get avatar and full name
          try {
            const userProfile = await getCurrentUser();
            console.log('[AuthContext] Profile loaded:', userProfile);

            // Combine firstName and lastName for display
            const displayName = userProfile.firstName && userProfile.lastName
              ? `${userProfile.firstName} ${userProfile.lastName}`
              : userProfile.fullName || userProfile.firstName || userProfile.lastName || decoded.username || '';

            setUser(prev => ({
              ...prev!,
              fullName: displayName,
              avatarUrl: userProfile.avatarUrl || userProfile.avatar,
              twoFactorEnabled: userProfile.twoFactorEnabled,
            }));
          } catch (error) {
            console.warn('[AuthContext] Failed to load profile on init:', error);
          }
        }
      } else {
        console.log('[AuthContext] No valid token found or token expired');
      }

      setLoading(false);
    };

    initAuth();
  }, []);

  const login = async (credentials: LoginRequest): Promise<LoginResponse> => {
    setLoading(true);
    try {
      console.log('[AuthContext] Attempting login for username:', credentials.username);

      const response = await apiLogin(credentials);
      setTokenState(response.accessToken);

      // Parse roles from backend response
      // Backend returns roles array like ["ROLE_USER", "ROLE_ADMIN"]
      const hasAdminRole = response.roles && response.roles.includes('ROLE_ADMIN');
      const role = hasAdminRole ? UserRole.ADMIN : UserRole.USER;

      console.log('[AuthContext] Login successful:', {
        id: response.id,
        username: response.username,
        email: response.email,
        roles: response.roles,
        determinedRole: role,
      });

      // Set basic user info immediately for fast login
      setUser({
        id: response.id,
        username: response.username,
        email: response.email,
        fullName: response.username,
        role: role,
        enabled: true,
        twoFactorEnabled: false,
        avatarUrl: response.avatarUrl,
      });

      // Fetch full profile in background (don't wait for it)
      getCurrentUser()
        .then(userProfile => {
          console.log('[AuthContext] Profile loaded after login:', userProfile);

          // Combine firstName and lastName for display
          const displayName = userProfile.firstName && userProfile.lastName
            ? `${userProfile.firstName} ${userProfile.lastName}`
            : userProfile.fullName || userProfile.firstName || userProfile.lastName;

          setUser(prev => ({
            ...prev!,
            fullName: displayName || prev!.fullName,
            avatarUrl: userProfile.avatarUrl || userProfile.avatar,
            twoFactorEnabled: userProfile.twoFactorEnabled,
          }));
        })
        .catch(error => {
          console.warn('[AuthContext] Failed to load profile after login:', error);
        });

      // Migrate anonymous view history to user account
      migrateViewsOnLogin().catch(error => {
        console.warn('[AuthContext] Failed to migrate view history:', error);
        // Don't block login flow if migration fails
      });

      return response;
    } catch (error: any) {
      console.error('[AuthContext] Login failed:', error);
      throw error;
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    console.log('[AuthContext] Logging out user');
    setUser(null);
    setTokenState(null);
    apiLogout();
    // Redirect is handled in apiLogout (auth.ts) with window.location.href
  };

  const isAuthenticated = !!user && !!token;
  const isAdmin = user?.role === UserRole.ADMIN;

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated,
        isAdmin,
        loading,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

/**
 * Hook to use authentication context
 */
export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
