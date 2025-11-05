# Next.js Frontend Integration Guide

## Overview

This Next.js 15.5.5 frontend application integrates with the Spring Boot backend to provide an admin dashboard with AI-powered clustering analytics.

## Architecture

### Rendering Strategy

The application uses **CSR (Client-Side Rendering)** for admin pages because:

1. **Dynamic Data**: Clustering data is real-time and changes frequently
2. **Authentication Required**: Admin pages require JWT token validation
3. **Interactive Features**: Real-time filtering and data manipulation
4. **No SEO Needed**: Admin dashboards don't need search engine indexing

### Authentication Flow

```
User Login → JWT Token → localStorage → AuthContext → Protected Routes
```

1. User submits credentials to `/api/auth/login`
2. Backend returns JWT token + user info
3. Token stored in `localStorage` (accessible client-side)
4. `AuthContext` provides auth state throughout app
5. `ProtectedRoute` component guards admin pages
6. Middleware redirects unauthorized users

## Project Structure

```
frontend/
├── src/
│   ├── app/                          # Next.js App Router
│   │   ├── layout.tsx                # Root layout with AuthProvider
│   │   ├── page.tsx                  # Home page
│   │   ├── login/
│   │   │   └── page.tsx              # Login page
│   │   ├── unauthorized/
│   │   │   └── page.tsx              # 403 page
│   │   └── admin/                    # Admin routes (protected)
│   │       ├── layout.tsx            # Admin layout with navigation
│   │       ├── page.tsx              # Admin dashboard home
│   │       └── analytics/
│   │           ├── users/
│   │           │   └── page.tsx      # User clustering (CSR)
│   │           └── products/
│   │               └── page.tsx      # Product clustering (CSR)
│   ├── components/
│   │   └── ProtectedRoute.tsx        # Client-side route guard
│   ├── contexts/
│   │   └── AuthContext.tsx           # Authentication context provider
│   ├── lib/
│   │   └── api/
│   │       ├── client.ts             # HTTP client with JWT auth
│   │       ├── auth.ts               # Auth API functions
│   │       └── clustering.ts         # Clustering API functions
│   ├── types/
│   │   └── auth.ts                   # TypeScript types for auth
│   └── middleware.ts                 # Next.js middleware for route protection
├── .env.local.example                # Environment variables template
└── package.json
```

## Setup Instructions

### 1. Install Dependencies

```bash
cd frontend
npm install
```

### 2. Configure Environment

Create `.env.local` from the example:

```bash
cp .env.local.example .env.local
```

Edit `.env.local`:

```env
# For local development
NEXT_PUBLIC_API_URL=http://localhost:8080

# For production
# NEXT_PUBLIC_API_URL=https://api.yourdomain.com
```

### 3. Run Development Server

```bash
npm run dev
```

The app will be available at `http://localhost:3000`

### 4. Build for Production

```bash
npm run build
npm start
```

## API Integration

### API Client (`lib/api/client.ts`)

The API client handles:
- JWT token management (localStorage)
- Authorization header injection
- Error handling
- Request/response formatting

**Usage:**

```typescript
import { get, post } from '@/lib/api/client';

// GET request with JWT auth
const data = await get('/api/endpoint');

// POST request with JWT auth
const response = await post('/api/endpoint', { data });
```

### Authentication API (`lib/api/auth.ts`)

Functions for authentication:
- `login(credentials)` - Login and store token
- `register(data)` - Register new user
- `logout()` - Clear token and redirect
- `decodeToken(token)` - Extract user info from JWT
- `isTokenExpired(token)` - Check token expiration

### Clustering API (`lib/api/clustering.ts`)

Functions for clustering endpoints:
- `getUserClusters()` - GET `/api/ai/clusters/users` (ADMIN only)
- `getProductClusters()` - GET `/api/ai/clusters/products` (ADMIN only)

## Authentication

### AuthContext

Global authentication state management:

```typescript
const { user, token, isAuthenticated, isAdmin, loading, login, logout } = useAuth();
```

**Properties:**
- `user` - Current user object
- `token` - JWT token string
- `isAuthenticated` - Boolean, true if logged in
- `isAdmin` - Boolean, true if user has ADMIN role
- `loading` - Boolean, true during auth state initialization
- `login(credentials)` - Login function
- `logout()` - Logout function

### ProtectedRoute Component

Client-side route protection:

```tsx
<ProtectedRoute requireAdmin={true}>
  <YourAdminComponent />
</ProtectedRoute>
```

**Features:**
- Shows loading spinner during auth check
- Redirects to `/login` if not authenticated
- Redirects to `/unauthorized` if not admin (when `requireAdmin={true}`)

### Middleware

Edge middleware for server-side route protection:

```typescript
// middleware.ts
export function middleware(request: NextRequest) {
  // Checks JWT token from cookies
  // Validates ADMIN role for /admin/* routes
  // Redirects unauthorized users
}
```

**Note:** Currently checks cookies for token. If using localStorage only, middleware will redirect to login page, then client-side `ProtectedRoute` will handle final authentication.

## Admin Pages

### User Clustering (`/admin/analytics/users`)

**Rendering:** CSR (Client-Side Rendering)

**Features:**
- Fetches all users with metrics (orders, spending, reviews)
- Segments users into 3 clusters:
  - **Low Value**: spending < 200,000 VND
  - **Medium Value**: 200,000 ≤ spending < 500,000 VND
  - **High Value**: spending ≥ 500,000 VND
- Displays Gemini AI analysis
- Interactive table with cluster filtering
- Real-time data updates

**API Endpoint:** `GET /api/ai/clusters/users`

**Response Format:**
```json
{
  "success": true,
  "totalUsers": 10,
  "users": [
    {
      "userId": 1,
      "username": "user1",
      "orders": 5,
      "spending": 349800.0,
      "reviews": 2
    }
  ],
  "analysis": "AI-generated text analysis...",
  "clusteringRules": {
    "lowValue": "spending < 200,000 VND",
    "mediumValue": "200,000 <= spending < 500,000 VND",
    "highValue": "spending >= 500,000 VND"
  },
  "timestamp": "..."
}
```

### Product Clustering (`/admin/analytics/products`)

**Rendering:** CSR (Client-Side Rendering)

**Features:**
- Fetches all products with performance metrics
- Segments products into 3 clusters:
  - **Best Sellers**: salesVolume > 50 OR revenue > 5,000,000 VND
  - **Moderate Performers**: 10 < salesVolume ≤ 50 OR 1,000,000 < revenue ≤ 5,000,000 VND
  - **Low Performers**: salesVolume ≤ 10 OR revenue ≤ 1,000,000 VND
- Displays Gemini AI analysis
- Interactive table with cluster filtering
- Product details (price, category, rating)

**API Endpoint:** `GET /api/ai/clusters/products`

**Response Format:**
```json
{
  "success": true,
  "totalProducts": 10,
  "products": [
    {
      "productId": 3,
      "name": "Áo thun nam basic",
      "price": 159000.0,
      "salesVolume": 2,
      "revenue": 318000.0,
      "reviewCount": 5,
      "averageRating": 4.5,
      "category": "Áo"
    }
  ],
  "analysis": "AI-generated text analysis...",
  "clusteringRules": {
    "bestSellers": "...",
    "moderatePerformers": "...",
    "lowPerformers": "..."
  },
  "timestamp": "..."
}
```

## Why CSR for Admin Pages?

### Advantages of CSR for This Use Case:

1. **Real-time Data**: Clustering data updates frequently, not suitable for static generation
2. **Authentication Required**: JWT token validation happens client-side (localStorage)
3. **No SEO Needed**: Admin dashboards don't need to be indexed by search engines
4. **Interactive Features**: Filtering, sorting, and real-time updates work better with CSR
5. **Simpler Architecture**: No need for server-side rendering complexity

### When to Use Other Strategies:

- **SSR (Server-Side Rendering)**: Public pages that need SEO and dynamic data
- **ISR (Incremental Static Regeneration)**: Content that updates periodically (e.g., blog posts, product listings)
- **SSG (Static Site Generation)**: Fully static content (e.g., marketing pages, documentation)

## Security Considerations

### JWT Token Storage

**Current Implementation:** localStorage (client-side)

**Pros:**
- Simple implementation
- Works across tabs/windows
- Persists across browser refreshes

**Cons:**
- Vulnerable to XSS attacks
- Not accessible in middleware

**Alternative:** HTTP-only cookies (more secure, works with middleware)

### Route Protection Layers

1. **Middleware (Edge)**: First line of defense, redirects unauthorized users
2. **ProtectedRoute Component**: Client-side guard, ensures auth state
3. **Backend Authorization**: Final enforcement via Spring Security

**Important:** Never rely solely on frontend protection. Backend must always validate JWT and roles.

### CORS Configuration

Backend must allow frontend origin:

```properties
# application.properties
cors.allowed-origins=http://localhost:3000,https://yourdomain.com
```

## Testing

### Local Testing

1. Start backend: `cd backend && mvn spring-boot:run`
2. Start frontend: `cd frontend && npm run dev`
3. Navigate to `http://localhost:3000/login`
4. Login with admin credentials
5. Access clustering pages

### Test Credentials

```
Username: admin
Password: admin123
Role: ADMIN
```

### API Testing

Use browser DevTools Network tab to verify:
- JWT token in Authorization header: `Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
- Response status codes (200 OK, 401 Unauthorized, 403 Forbidden)
- Response data format

## Deployment

### Environment Variables

Set the production backend URL:

```env
NEXT_PUBLIC_API_URL=https://api.yourdomain.com
```

### Build and Deploy

```bash
npm run build
npm start
```

### Docker Deployment

```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./package.json
EXPOSE 3000
CMD ["npm", "start"]
```

## Troubleshooting

### Issue: "Unauthorized" or "Token expired"

**Solution:**
1. Check if backend is running
2. Verify `NEXT_PUBLIC_API_URL` in `.env.local`
3. Login again to get fresh token
4. Check JWT expiration time in backend config

### Issue: CORS errors

**Solution:**
1. Add frontend URL to backend CORS config
2. Restart backend server
3. Clear browser cache

### Issue: Middleware redirects even when authenticated

**Solution:**
1. Middleware currently checks cookies, not localStorage
2. Option 1: Store token in HTTP-only cookie during login
3. Option 2: Rely on client-side `ProtectedRoute` only (remove middleware check)

## Next Steps

### Recommended Enhancements:

1. **Add Charts**: Use Chart.js or Recharts for scatter plot visualizations
2. **Token Refresh**: Implement automatic token refresh before expiration
3. **HTTP-only Cookies**: Move JWT storage from localStorage to cookies
4. **Loading States**: Add skeleton loaders for better UX
5. **Error Boundaries**: Add React error boundaries for graceful error handling
6. **Unit Tests**: Add Jest tests for components and API functions
7. **E2E Tests**: Add Playwright tests for critical user flows

## Support

For issues or questions:
- Backend API documentation: See `ADMIN_API_DOCUMENTATION.md`
- Spring Boot backend: See `backend/` directory
- Next.js documentation: https://nextjs.org/docs
