# E-Commerce Fashion Platform

A full-stack e-commerce fashion platform built with Spring Boot, Next.js, and modern cloud technologies.

## 🏗️ Architecture

### Backend
- **Framework**: Spring Boot 3.5.6
- **Database**: PostgreSQL 16
- **Cache**: Redis 7
- **Message Queue**: Apache Kafka 7.5
- **Search Engine**: Elasticsearch 8.11
- **Reverse Proxy**: Nginx
- **Container**: Docker & Docker Compose
- **AI/ML**: Spring AI with PostgresML

### Frontend
- **Framework**: Next.js 15
- **UI**: React 19 + Tailwind CSS v4
- **Language**: TypeScript

### Cloud Services
- **Image Storage**: Cloudinary (folder-specific configuration)
- **Payment Gateway**: PayPal (Sandbox & Production)

## ⚖️ Architecture Decisions & Trade-offs

Các quyết định kiến trúc có chủ đích (và giới hạn của chúng):

- **Modular monolith, không phải microservices** — code tổ chức theo feature (`product/`, `order/`, `payment/`, `auth/`...), mỗi package chứa đủ controller + service + repository + entity của domain đó. Với quy mô một team nhỏ, monolith triển khai đơn giản và dễ debug hơn; ranh giới theo feature giúp tách thành service riêng sau này nếu cần.
- **Kafka producer và consumer chạy trong cùng một ứng dụng** — Kafka ở đây dùng để xử lý bất đồng bộ (gửi email, sự kiện đơn hàng) thay vì giao tiếp giữa các service. Trade-off: không có lợi ích scale/isolation của consumer tách riêng, nhưng giữ được mô hình event-driven và retry/replay của Kafka mà không phải vận hành thêm service. Khi tách consumer thành worker riêng, code gần như không đổi. Kafka có thể tắt qua `spring.kafka.enabled=false` (fallback xử lý đồng bộ) để chạy trên hạ tầng free-tier.
- **Schema do Flyway quản lý** (`backend/src/main/resources/db/migration`) — Hibernate chỉ `validate`, không tự sửa bảng. Mọi thay đổi schema là một migration mới có version, review được trong PR.
- **Service không có interface riêng** — interface chỉ được tạo khi có nhiều implementation thật (ví dụ chuỗi fallback email Brevo → SendGrid → SMTP nằm trong `email/`). Với service một implementation, class cụ thể + constructor injection là đủ để test bằng Mockito.
- **Elasticsearch/Kafka tắt được bằng feature flag** — bản demo free-tier (Render 512MB) chạy Postgres + Redis; full stack (Kafka + ES) chạy qua Docker Compose. Đây là quyết định chi phí có chủ đích, không phải thiếu sót.

## 🔒 Security Features (OWASP Top 10 Compliant)

1. **Authentication**: JWT-based with refresh tokens
2. **Password Encryption**: BCrypt with strength 12
3. **CSRF Protection**: Cookie-based CSRF tokens
4. **SQL Injection Prevention**: JPA parameterized queries
5. **XSS Protection**: Content Security Policy (CSP)
6. **Secure Headers**: HSTS, X-Frame-Options, etc.
7. **Rate Limiting**: Bucket4j integration
8. **Account Security**: Account lockout after failed attempts
9. **Session Management**: Redis-based stateless sessions
10. **Input Validation**: Jakarta Validation

## 📦 Key Features

### Product Management
- ✅ Multiple images per product with Cloudinary storage
- ✅ Advanced search with Elasticsearch
- ✅ Filtering by category, price, size, color, brand
- ✅ Pagination support
- ✅ Product reviews and ratings
- ✅ Inventory management
- ✅ Featured products

### Order Management
- ✅ Shopping cart with Redis caching
- ✅ Order tracking
- ✅ Multiple order statuses
- ✅ Order history
- ✅ Async order processing with Kafka

### Payment
- ✅ PayPal integration (Sandbox & Production)
- ✅ Payment webhooks
- ✅ Refund support
- ✅ Payment history

### AI Features
- ✅ Product clustering with Spring AI
- ✅ Vector embeddings for recommendations
- ✅ PostgresML integration

### User Management
- ✅ User registration & authentication
- ✅ Role-based access control (USER, ADMIN, MODERATOR)
- ✅ Multiple shipping addresses
- ✅ Order history
- ✅ Account security

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Node.js 20+
- Docker & Docker Compose
- Maven 3.9+

### Environment Variables

Copy the example files and fill in your own values (never commit real secrets):

```bash
# Backend
cp backend/.env.example backend/.env

# Frontend
cp frontend/.env.example frontend/.env.local
```

See [backend/.env.example](backend/.env.example) for the full list of required
variables (database, JWT, Cloudinary, PayPal, Brevo email, AI keys) with notes
on where to obtain each one.

### Running with Docker (Recommended)

#### 1. Start all services:
```bash
cd backend
docker-compose up -d
```

This will start:
- PostgreSQL (port 5432)
- Redis (port 6379)
- Kafka & Zookeeper (ports 9092, 9093, 2181)
- Elasticsearch (ports 9200, 9300)
- Spring Boot App (port 8081)
- Nginx (ports 80, 443)

#### 2. Start Next.js frontend:
```bash
cd frontend
npm install
npm run dev
```

Frontend will be available at: http://localhost:3000

### Running Locally (Development)

#### Backend (Port 8080 for Postman testing)
```bash
cd backend

# Start infrastructure services only
docker-compose up -d postgres redis kafka zookeeper elasticsearch

# Run Spring Boot locally
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Backend API: http://localhost:8080/api

#### Frontend
```bash
cd frontend
npm install
npm run dev
```

Frontend: http://localhost:3000

### Port Configuration

| Service | Docker Port | Local Port | Purpose |
|---------|-------------|------------|---------|
| Spring Boot | 8081 | 8080 | Different ports for Docker vs Local |
| PostgreSQL | 5432 | 5433 | Database |
| Redis | 6379 | 6379 | Cache & Sessions |
| Kafka | 9092, 9093 | 9092, 9093 | Message Queue |
| Elasticsearch | 9200 | 9200 | Search Engine |
| Next.js | - | 3000 | Frontend |
| Nginx | 80, 443 | - | Reverse Proxy |

## 📁 Project Structure

```
.
├── backend/
│   ├── src/main/java/com/ut/edu/backend/   # Package-by-feature (modular monolith)
│   │   ├── product/         # Product + images + view history (controller, service, repo, entity)
│   │   ├── category/        # Category tree
│   │   ├── cart/            # Cart + Redis cart cache
│   │   ├── order/           # Orders + admin order management
│   │   ├── payment/         # Payments + PayPal integration
│   │   ├── coupon/          # Coupons + usage tracking
│   │   ├── review/          # Reviews + review images
│   │   ├── wishlist/        # Wishlist
│   │   ├── user/            # Users, roles, addresses
│   │   ├── auth/            # Login, OTP, 2FA (TOTP), sessions, tokens
│   │   ├── ai/              # DeepSeek recommendations, clustering, chatbot
│   │   ├── email/           # Brevo → SendGrid → SMTP fallback chain
│   │   ├── media/           # Cloudinary image upload
│   │   ├── dashboard/       # Admin dashboard aggregations
│   │   ├── kafka/           # Kafka producers/consumers
│   │   ├── security/        # JWT filter, rate limiting, XSS filter
│   │   ├── config/          # Cross-cutting Spring config
│   │   ├── exception/       # Global exception handling
│   │   ├── validation/      # Custom Jakarta validators
│   │   └── common/          # BaseEntity + shared utilities
│   ├── src/main/resources/
│   │   ├── db/migration/                  # Flyway migrations (V1__baseline.sql, ...)
│   │   ├── application.properties         # Base config
│   │   ├── application-dev.properties     # Development
│   │   ├── application-staging.properties # Staging
│   │   └── application-prod.properties    # Production
│   ├── nginx/
│   │   ├── nginx.conf       # Nginx main config
│   │   └── conf.d/          # Nginx site configs
│   ├── Dockerfile           # Spring Boot container
│   ├── docker-compose.yaml  # All services
│   └── pom.xml             # Maven dependencies
│
├── frontend/
│   ├── src/
│   │   ├── app/            # Next.js pages
│   │   ├── components/     # React components
│   │   ├── context/        # React contexts
│   │   ├── lib/            # Utilities
│   │   └── types/          # TypeScript types
│   ├── public/             # Static assets
│   └── package.json        # Dependencies
│
└── README.md               # This file
```

## 🔧 Available Profiles

### Development (`dev`)
- Local PostgreSQL: localhost:5433
- Debug logging enabled
- Swagger UI enabled
- Hot reload enabled
- CORS: localhost:3000

### Staging (`staging`)
- Docker services
- Moderate logging
- Swagger UI enabled
- CORS: staging URLs

### Production (`prod`)
- Docker services
- Minimal logging
- Swagger UI disabled
- HTTPS enforced
- Security headers enforced
- Environment variables required

## 🗄️ Database Migrations (Flyway)

The schema is managed **exclusively by Flyway** (`backend/src/main/resources/db/migration`).
Hibernate only validates (`ddl-auto=validate`). Rules:

- Every schema change = a **new** file `V5__short_description.sql`, `V6__...` — never edit an applied migration, never run ad-hoc SQL on a managed database.
- The same migration files run on every environment; only the connection differs.
- Neon (production) was baselined at version 3 on 2026-07-14 — from now on deploys apply pending migrations automatically.

### Automatic (default — migrations run on app startup, no separate Flyway command)

Spring Boot invokes Flyway during boot: it checks `flyway_schema_history` and applies
any pending migrations before the app starts serving requests. Just run the app as usual:

| Environment | How migrations run |
|---|---|
| Dev | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` — Flyway migrates local Postgres (port 5433) on startup |
| Test | `./mvnw test` — Testcontainers spins up a fresh Postgres and Flyway applies V1 → latest |
| Production (Render → Neon) | App boots with `SPRING_PROFILES_ACTIVE=prod` on deploy → Flyway applies pending migrations |

### Manual (Flyway CLI via Docker — inspect or fix)

Pick an environment by setting env vars (PowerShell), then the command is identical everywhere.

```powershell
# ===== DEV (local Docker Postgres; container must use host.docker.internal, not localhost) =====
$env:FLYWAY_URL      = "jdbc:postgresql://host.docker.internal:5433/thuctaptotnghiep"
$env:FLYWAY_USER     = "postgres"
$env:FLYWAY_PASSWORD = "postgres"

# ===== PROD (Neon — get the password from Neon Console, never commit it) =====
$env:FLYWAY_URL      = "jdbc:postgresql://<neon-host>:5432/neondb?sslmode=require"
$env:FLYWAY_USER     = "neondb_owner"
$env:FLYWAY_PASSWORD = "<neon-password>"
```

```powershell
docker run --rm -v "${PWD}\backend\src\main\resources\db\migration:/flyway/sql" `
  -e FLYWAY_URL -e FLYWAY_USER -e FLYWAY_PASSWORD `
  flyway/flyway:11 info
```

| Command (replace `info`) | Purpose |
|---|---|
| `info` | Show current version + pending migrations (harmless — run before/after anything) |
| `validate` | Compare SQL file checksums against the schema history |
| `migrate` | Apply pending migrations without waiting for a deploy |
| `baseline -baselineVersion=N` | Register an existing schema (one-time only — already done for Neon) |
| `repair` | Fix the history table (failed migration record / checksum mismatch) |

> ⚠️ Never run `clean` against production — it drops every table. Flyway ships with `cleanDisabled=true`; leave it that way.
>
> Tip: before a production deploy, run `info` against Neon to see exactly which migrations are about to be applied.

## 📡 API Documentation

When running in development or staging, Swagger UI is available at:
- Local: http://localhost:8080/swagger-ui.html
- Docker: http://localhost:8081/swagger-ui.html

### Main API Endpoints

#### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login
- `POST /api/auth/refresh` - Refresh token
- `POST /api/auth/logout` - Logout

#### Products
- `GET /api/products` - List products (with pagination, search, filter)
- `GET /api/products/{id}` - Get product details
- `GET /api/products/slug/{slug}` - Get by slug
- `POST /api/admin/products` - Create product (Admin)
- `PUT /api/admin/products/{id}` - Update product (Admin)
- `DELETE /api/admin/products/{id}` - Delete product (Admin)

#### Categories
- `GET /api/categories` - List all categories
- `GET /api/categories/{id}` - Get category details
- `GET /api/categories/{id}/products` - Get products in category

#### Cart
- `GET /api/cart` - Get user's cart
- `POST /api/cart/items` - Add item to cart
- `PUT /api/cart/items/{id}` - Update cart item
- `DELETE /api/cart/items/{id}` - Remove cart item
- `DELETE /api/cart/clear` - Clear cart

#### Orders
- `GET /api/orders` - Get user's orders
- `GET /api/orders/{id}` - Get order details
- `POST /api/orders` - Create order
- `PUT /api/orders/{id}/cancel` - Cancel order

#### Payments
- `POST /api/payments/create` - Create PayPal payment
- `POST /api/payments/capture` - Capture payment
- `POST /api/payments/webhook/paypal` - PayPal webhook

#### Reviews
- `GET /api/products/{id}/reviews` - Get product reviews
- `POST /api/reviews` - Create review
- `PUT /api/reviews/{id}` - Update review
- `DELETE /api/reviews/{id}` - Delete review

## 🧪 Testing

### Backend
```bash
cd backend
./mvnw test
```
- **Unit tests** (JUnit 5 + Mockito): OTP lifecycle (`OtpServiceTest`), JWT generation/validation/tampering (`JwtTokenProviderTest`)
- **Integration test** (Testcontainers — requires Docker): boots the full Spring context against real PostgreSQL + Redis containers and proves the Flyway baseline migrates a fresh database (`BackendApplicationTests`)
- **Coverage**: JaCoCo report at `target/site/jacoco/index.html` (runs on JDK ≤ 24, e.g. in CI)

### Frontend
```bash
cd frontend
npm run lint
```
(Vitest + React Testing Library planned — see TODO.md Phase 4)

### Test with Postman
Import the Postman collection (to be created) for API testing:
- Local: http://localhost:8080/api
- Docker: http://localhost:8081/api

## 🔑 Default Admin Account

After running the application, you can create an admin account through registration and manually update the database:

```sql
UPDATE users SET roles = 'ADMIN' WHERE username = 'your-username';
```

## 🎨 Cloudinary Folder Structure

Images are organized in Cloudinary with the following structure:
```
products/
  ├── {category}/
  │   └── {product-id}/
  │       ├── image-1.jpg
  │       ├── image-2.jpg
  │       └── ...
```

## 🤖 AI Product Clustering

The platform uses Spring AI with PostgresML for:
- Product similarity detection
- Smart product recommendations
- Category suggestion
- Search query enhancement

## 📊 Monitoring

### Health Check
- http://localhost:8080/actuator/health (local)
- http://localhost:8081/actuator/health (docker)

### Metrics
- http://localhost:8080/actuator/metrics
- http://localhost:8080/actuator/prometheus

## 🚢 Deployment

### Docker Production
```bash
# Set environment variables
export SPRING_PROFILES_ACTIVE=prod
export JWT_SECRET=your-production-secret
# ... other environment variables

# Build and run
docker-compose up -d
```

### SSL Configuration
1. Obtain SSL certificates (Let's Encrypt recommended)
2. Update nginx configuration
3. Enable HTTPS enforcement in production profile

## 📝 License

This project is licensed under the MIT License.

## 👥 Contributors

- Your Name

## 📧 Support

For issues and questions, please open an issue on GitHub.

---

Built with ❤️ using Spring Boot, Next.js, and modern cloud technologies.
