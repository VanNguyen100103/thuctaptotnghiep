/**
 * Next.js Middleware for route protection
 * Runs on Edge Runtime for better performance
 *
 * DISABLED: Currently using client-side protection with ProtectedRoute component
 * because tokens are stored in localStorage, not accessible in middleware.
 *
 * To enable middleware-based protection:
 * 1. Store JWT token in httpOnly cookies instead of localStorage
 * 2. Update login() to set cookie: document.cookie = `accessToken=${token}; path=/; secure; samesite=strict`
 * 3. Uncomment the middleware function below
 */

import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(_request: NextRequest) {
  // Middleware is disabled - all protection is handled client-side by ProtectedRoute
  // This is because tokens are in localStorage which is not accessible in Edge Runtime
  return NextResponse.next();
}

// Only run middleware on API routes if needed
export const config = {
  matcher: [],
};


