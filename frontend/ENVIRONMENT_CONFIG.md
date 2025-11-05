# Environment Configuration Guide

## Backend URL Configuration

Tùy vào cách bạn chạy backend, sử dụng URL tương ứng trong file `.env.local`:

### Option 1: Docker Mode (Recommended)

**Khi nào dùng:** Backend chạy qua `docker-compose up`

**Configuration:**
```env
NEXT_PUBLIC_API_URL=http://localhost
```

**Giải thích:**
- Backend chạy 3 instances trong Docker
- Nginx load balancer ở port 80
- Nginx forward requests tới backend instances
- **KHÔNG** cần thêm `/api` vì Spring Boot đã có `context-path=/api`

**Example API calls:**
```javascript
// Frontend code
const response = await getUserClusters();
// → Calls: http://localhost/api/ai/clusters/users
// → Nginx forwards to: http://backend:8081/api/ai/clusters/users
```

---

### Option 2: Development Mode (Direct Backend)

**Khi nào dùng:** Backend chạy trực tiếp với `mvn spring-boot:run` (không qua Docker)

**Configuration:**
```env
NEXT_PUBLIC_API_URL=http://localhost:8081
```

**Giải thích:**
- Backend chạy trực tiếp ở port 8081
- Không có Nginx load balancer
- Truy cập trực tiếp vào Spring Boot server

**Example API calls:**
```javascript
// Frontend code
const response = await getUserClusters();
// → Calls: http://localhost:8081/api/ai/clusters/users
```

---

## URL Structure Explained

### Backend Configuration

Spring Boot có **context path** được set trong `application-{profile}.properties`:

```properties
server.servlet.context-path=/api
```

Điều này có nghĩa **mọi endpoint đều có prefix `/api`** automatically.

**Example:**
- Controller: `@RequestMapping("/ai")`
- Method: `@GetMapping("/clusters/users")`
- **Full URL:** `/api/ai/clusters/users` (context-path tự động thêm vào)

### Nginx Configuration (Docker Mode)

Nginx config trong `backend/nginx/conf.d/default.conf`:

```nginx
location /api/ {
    proxy_pass http://backend_servers;  # Forward to backend
}
```

**Flow:**
1. Client request: `http://localhost/api/ai/clusters/users`
2. Nginx matches: `location /api/`
3. Nginx forwards to backend: `http://backend:8081/api/ai/clusters/users`
4. Backend processes: `/api/ai/clusters/users` ✅

---

## Quick Setup

### Step 1: Check Backend Mode

**Docker mode:**
```bash
docker ps | grep backend
# Should show: backend-backend-1, backend-backend-2, backend-backend-3, ecommerce-nginx
```

**Direct mode:**
```bash
# Backend running with mvn spring-boot:run
# Check terminal for: "Tomcat started on port(s): 8081"
```

### Step 2: Configure .env.local

**For Docker:**
```bash
cd frontend
echo "NEXT_PUBLIC_API_URL=http://localhost" > .env.local
```

**For Direct:**
```bash
cd frontend
echo "NEXT_PUBLIC_API_URL=http://localhost:8081" > .env.local
```

### Step 3: Verify Configuration

```bash
cd frontend
npm run dev
```

Navigate to `http://localhost:3000/login` and try logging in.

---

## Troubleshooting

### Issue: CORS Error

**Cause:** Backend không allow frontend origin

**Solution:** Check `application.properties`:
```properties
cors.allowed.origins=http://localhost:3000,http://localhost:8080
```

Add `http://localhost:3000` if missing, then restart backend.

### Issue: Network Error / Connection Refused

**Docker mode:**
```bash
# Check if Nginx is running
docker ps | grep nginx

# Check Nginx logs
docker logs ecommerce-nginx

# Check backend logs
docker logs backend-backend-1
```

**Direct mode:**
```bash
# Check if backend is running on port 8081
curl http://localhost:8081/api/actuator/health
```

### Issue: 404 Not Found

**Cause:** Wrong API URL or missing `/api` prefix

**Check:**
1. Frontend `.env.local` has correct URL
2. Backend context-path is `/api`
3. Controller mappings are correct

**Test API directly:**
```bash
# Docker mode
curl http://localhost/api/actuator/health

# Direct mode
curl http://localhost:8081/api/actuator/health
```

Should return: `{"status":"UP"}`

---

## Current Status

Based on your Docker containers screenshot:

- ✅ Nginx running on port 80:80 and 443:443
- ✅ Backend instances: backend-1, backend-2, backend-3
- ✅ Redis, Kafka, Zookeeper, PostgreSQL running

**Recommended configuration:**
```env
NEXT_PUBLIC_API_URL=http://localhost
```

---

## Testing the Configuration

After setting up `.env.local`, test the API integration:

```bash
cd frontend
npm run dev
```

1. Navigate to `http://localhost:3000/login`
2. Login with admin credentials: `admin` / `admin123`
3. Go to `http://localhost:3000/admin/analytics/users`
4. Check browser DevTools → Network tab
5. Should see request to: `http://localhost/api/ai/clusters/users`
6. Should receive 200 OK with clustering data

If you see CORS errors or connection errors, check backend logs and Nginx logs.
