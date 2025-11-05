'use client';

/**
 * Client-side route protection component
 * Use this for CSR pages that need authentication
 */

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requireAdmin?: boolean;
}

export default function ProtectedRoute({ children, requireAdmin = false }: ProtectedRouteProps) {
  const { isAuthenticated, isAdmin, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    console.log('[ProtectedRoute] Auth check:', {
      loading,
      isAuthenticated,
      isAdmin,
      requireAdmin,
      currentPath: window.location.pathname,
    });

    // Only redirect if loading is done
    if (!loading) {
      if (!isAuthenticated) {
        console.log('[ProtectedRoute] User not authenticated, redirecting to login');
        const currentPath = window.location.pathname;
        router.replace(`/login?redirect=${encodeURIComponent(currentPath)}`);
      } else if (requireAdmin && !isAdmin) {
        console.log('[ProtectedRoute] User not admin, redirecting to unauthorized');
        router.replace('/unauthorized');
      } else {
        console.log('[ProtectedRoute] Access granted');
      }
    }
  }, [loading, isAuthenticated, isAdmin, requireAdmin, router]);

  // Show loading spinner while checking auth
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  // If not authenticated, show nothing (will redirect in useEffect)
  if (!isAuthenticated) {
    return null;
  }

  // If admin required but user is not admin, show nothing (will redirect in useEffect)
  if (requireAdmin && !isAdmin) {
    return null;
  }

  // All checks passed, render children
  return <>{children}</>;
}
