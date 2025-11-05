# CSRF Token Optimization

## Vấn đề ban đầu

Spring Security sử dụng **single-use CSRF tokens** - mỗi token chỉ dùng được **1 lần**. Điều này gây ra vấn đề:

1. **Mỗi request cần CSRF token mới**
2. **Phải gọi `/api/auth/csrf-token` liên tục** trước mỗi state-changing request
3. **Không hiệu quả** cho Single Page Application (SPA)

## Giải pháp Frontend

Thay vì gọi `/csrf-token` trước mỗi request, đã implement **automatic CSRF token handling**:

### 1. Tự động lấy CSRF token từ cookie

```typescript
export const getCsrfToken = (): string | null => {
  if (typeof window === 'undefined') return null;

  const cookies = document.cookie.split(';');
  for (const cookie of cookies) {
    const [name, value] = cookie.trim().split('=');
    if (name === 'XSRF-TOKEN') {
      return decodeURIComponent(value);
    }
  }
  return null;
};
```

### 2. Tự động fetch token mới khi cần

```typescript
async function fetchCsrfToken(): Promise<string | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/csrf-token`, {
      method: 'GET',
      credentials: 'include', // Important: include cookies
    });

    if (response.ok) {
      // Token is set in cookie automatically by backend
      await new Promise(resolve => setTimeout(resolve, 100));
      return getCsrfToken();
    }
  } catch (error) {
    console.error('Failed to fetch CSRF token:', error);
  }
  return null;
}
```

### 3. Tự động thêm CSRF token vào header

```typescript
// Add CSRF token for state-changing requests (POST, PUT, PATCH, DELETE)
const method = options.method?.toUpperCase() || 'GET';
if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
  const csrfToken = getCsrfToken();
  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
  }
}
```

### 4. Auto-retry với token mới khi gặp 403

```typescript
// Handle CSRF token missing/invalid (403 Forbidden)
if (response.status === 403 && retryWithCsrf) {
  console.log('CSRF token invalid or missing, fetching new token...');

  // Fetch new CSRF token
  const newCsrfToken = await fetchCsrfToken();

  if (newCsrfToken) {
    console.log('Retrying request with new CSRF token');
    // Retry request with new CSRF token (prevent infinite loop)
    return apiRequest<T>(endpoint, options, false);
  }
}
```

## Flow hoạt động

### Request thành công (Token hợp lệ)

```
1. User gửi POST /api/cart/items
2. Frontend lấy CSRF token từ cookie XSRF-TOKEN
3. Frontend thêm header: X-XSRF-TOKEN: {token}
4. Backend verify token
5. Backend xử lý request
6. Backend gửi token mới trong cookie
7. Response 200 OK
```

### Request thất bại (Token hết hạn)

```
1. User gửi POST /api/cart/items
2. Frontend lấy CSRF token từ cookie (token cũ/hết hạn)
3. Frontend thêm header: X-XSRF-TOKEN: {old_token}
4. Backend verify token → FAIL
5. Response 403 Forbidden
6. Frontend tự động gọi GET /api/auth/csrf-token
7. Backend tạo token mới, gửi trong cookie
8. Frontend lấy token mới từ cookie
9. Frontend retry POST /api/cart/items với token mới
10. Backend verify token → SUCCESS
11. Response 200 OK
```

## Lợi ích

1. ✅ **Transparent cho developer** - Không cần manually handle CSRF token
2. ✅ **Tự động retry** - Request tự động retry khi token hết hạn
3. ✅ **Giảm số lượng requests** - Chỉ fetch token mới khi cần
4. ✅ **Better UX** - User không thấy lỗi CSRF
5. ✅ **Secure** - Vẫn giữ CSRF protection

## Code changes

### File updated: `frontend/src/lib/api/client.ts`

**Thêm functions:**
- `getCsrfToken()` - Đọc CSRF token từ cookie
- `fetchCsrfToken()` - Fetch token mới từ backend

**Updated `apiRequest()` function:**
- Tự động thêm `X-XSRF-TOKEN` header cho POST/PUT/PATCH/DELETE
- Tự động thêm `credentials: 'include'` để gửi cookies
- Auto-retry khi gặp 403 Forbidden

## Testing

### Test case 1: First request (No token yet)

```bash
# Frontend
POST /api/cart/items
Header: (no CSRF token)

# Backend responds 403
# Frontend auto-fetches token
GET /api/auth/csrf-token

# Backend sets cookie: XSRF-TOKEN=abc123
# Frontend retries
POST /api/cart/items
Header: X-XSRF-TOKEN: abc123

# Success: 200 OK
```

### Test case 2: Subsequent requests (Token exists)

```bash
# Frontend
POST /api/wishlist
Header: X-XSRF-TOKEN: abc123 (from cookie)

# Backend validates and responds
# Sets new token in cookie: XSRF-TOKEN=xyz789

# Success: 200 OK

# Next request automatically uses new token
POST /api/orders
Header: X-XSRF-TOKEN: xyz789

# Success: 200 OK
```

### Test case 3: Token expired

```bash
# Token trong cookie đã expired/invalid
POST /api/orders
Header: X-XSRF-TOKEN: expired_token

# Backend: 403 Forbidden
# Frontend auto-fetches new token
GET /api/auth/csrf-token

# Retry with new token
POST /api/orders
Header: X-XSRF-TOKEN: new_token

# Success: 200 OK
```

## Important Notes

### 1. Cookies must be enabled

```typescript
credentials: 'include'  // Bắt buộc để gửi/nhận cookies
```

### 2. CORS configuration

Backend phải allow credentials:

```properties
cors.allow.credentials=true
cors.allowed.origins=http://localhost:3000
```

### 3. Cookie settings

Backend CSRF token cookie settings:

```java
CookieCsrfTokenRepository.withHttpOnlyFalse()
```

**`httpOnly: false`** - Cho phép JavaScript đọc cookie (cần thiết để lấy token)

### 4. Security consideration

CSRF protection chỉ cần thiết khi dùng **cookie-based authentication**. Với **JWT trong localStorage**, CSRF không phải vấn đề.

**Tuy nhiên**, backend hiện tại có cả:
- JWT authentication (Authorization header)
- CSRF protection (cookies)

→ Double protection, an toàn nhưng có thể over-engineering

## Alternative: Disable CSRF for JWT endpoints

Nếu muốn đơn giản hơn, có thể disable CSRF cho JWT-authenticated endpoints:

```java
// SecurityConfig.java
.csrf(csrf -> csrf
    .ignoringRequestMatchers(
        "/ai/**",  // JWT auth, không cần CSRF
        "/cart/**",
        "/orders/**",
        // ... other JWT endpoints
    )
)
```

**Ưu điểm:**
- Không cần handle CSRF token ở frontend
- Đơn giản hơn
- Vẫn secure với JWT

**Nhược điểm:**
- Mất CSRF protection layer
- Phụ thuộc hoàn toàn vào JWT security

## Recommendation

**Current solution** (auto-retry CSRF) là tốt nhất vì:
1. ✅ Giữ được cả 2 layers security (JWT + CSRF)
2. ✅ Transparent cho frontend developer
3. ✅ Không cần thay đổi backend
4. ✅ Works out of the box

---

**Status:** ✅ **Implemented and ready to test**

**Next step:** Restart Next.js dev server và test các API calls
