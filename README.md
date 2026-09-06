# Tryum

A multi-tenant SaaS retail-management platform (KiotViet-style) for small businesses — retail, F&B, beauty, hospitality. Each store registers, gets its own storefront + owner dashboard + subscription plan, all on one shared codebase and database (`store_id`-scoped).

## 🌐 Live Demo

- **Frontend**: https://thuctaptotnghiep-frontend.vercel.app
- **Backend API (health)**: https://thuctaptotnghiep-backend.onrender.com/api/actuator/health
- **Uptime status**: https://stats.uptimerobot.com/8QPXy2LFrm

> Backend chạy trên Render free tier — nếu service vừa ngủ, lần gọi đầu tiên có thể mất ~1 phút để đánh thức.

## 🏗️ Architecture

### Backend
- **Framework**: Spring Boot 3.5.6 (Java 17)
- **Database**: PostgreSQL 16, schema managed exclusively by Flyway
- **Cache / Sessions**: Redis 7
- **Async messaging**: Apache Kafka — local Docker (Zookeeper + Kafka) for dev, managed Aiven Kafka free tier (SASL_SSL) in production; can be disabled entirely via `spring.kafka.enabled=false` (falls back to synchronous processing)
- **Multi-tenancy**: shared schema + `store_id` discriminator — a Hibernate `@Filter` on every tenant-owned entity, enabled per-request by `TenantResolverFilter` (resolves the tenant from the JWT for staff/owner routes, or from the storefront URL slug for public routes)
- **Auth**: JWT + refresh tokens, email OTP, TOTP 2FA, Bucket4j rate limiting
- **AI**: a storefront chatbot — Gemini as primary provider with an automatic Groq fallback, using tool-calling against live product/category/store-policy data (no embeddings, no vector DB, no Spring AI/PostgresML — see the `ai/` package)

### Frontend
- **Framework**: Angular 22 (standalone components, signals)
- **Styling**: Tailwind CSS v4
- **Language**: TypeScript
- **Testing**: Vitest (via `ng test`)

### Cloud services actually wired in
- **Images**: Cloudinary
- **Payments**: PayPal (checkout + subscription billing), MoMo, SePay (VietQR bank-transfer webhook)
- **Shipping**: GHN (Giao Hàng Nhanh)
- **Email**: Brevo HTTP API (primary, works on Render) → Gmail SMTP (fallback, local/VPS only)
- **AI**: Google Gemini + Groq (both free tier)

## 👤 Roles & multi-tenancy

Every store-scoped endpoint is guarded by **`StoreRole`**, not a generic global role:

- `OWNER` / `MANAGER` / `STAFF` — scoped to one store (a user's store + role live in their JWT)
- `SUPER_ADMIN` — platform-wide, not tied to any store (the SaaS operator's own admin routes under `/platform/**`)

(`user/Role.java` — `USER`/`ADMIN`/`MODERATOR` — still exists in the codebase but doesn't gate anything; every real `@PreAuthorize` check uses `StoreRole`.)

## ⚖️ Architecture Decisions & Trade-offs

Các quyết định kiến trúc có chủ đích (và giới hạn của chúng):

- **Modular monolith, không phải microservices** — code tổ chức theo feature (`product/`, `order/`, `payment/`, `auth/`...), mỗi package chứa đủ controller + service + repository + entity của domain đó. Với quy mô một team nhỏ, monolith triển khai đơn giản và dễ debug hơn; ranh giới theo feature giúp tách thành service riêng sau này nếu cần.
- **Kafka producer và consumer chạy trong cùng một ứng dụng** — Kafka ở đây dùng để xử lý bất đồng bộ (gửi email, sự kiện đơn hàng) thay vì giao tiếp giữa các service. Trade-off: không có lợi ích scale/isolation của consumer tách riêng, nhưng giữ được mô hình event-driven và retry/replay của Kafka mà không phải vận hành thêm service. Khi tách consumer thành worker riêng, code gần như không đổi. Kafka có thể tắt qua `spring.kafka.enabled=false` (fallback xử lý đồng bộ) để chạy trên hạ tầng free-tier.
- **Schema do Flyway quản lý** (`backend/src/main/resources/db/migration`) — Hibernate chỉ `validate`, không tự sửa bảng. Mọi thay đổi schema là một migration mới có version, review được trong PR.
- **Service không có interface riêng** — interface chỉ được tạo khi có nhiều implementation thật (ví dụ chuỗi fallback email Brevo → Gmail SMTP nằm trong `email/`, hoặc cặp `GeminiProvider`/`GroqProvider` cùng implement `AiProvider` trong `ai/`). Với service một implementation, class cụ thể + constructor injection là đủ để test bằng Mockito.
- **AI chat dùng tool-calling trên dữ liệu sống, không dùng RAG/embeddings** — mỗi lượt chat, model gọi tool (`search_products`, `get_store_policies`...) query thẳng DB hiện tại thay vì đọc từ index/vector DB đã đánh trước. Đổi lại tốc độ real-time (sản phẩm vừa sửa/import là AI thấy ngay) và không cần vector DB trả phí, nhưng mỗi câu hỏi tốn thêm 1-2 lượt gọi LLM cho việc chọn tool.

## 🔒 Security Features (OWASP Top 10 Compliant)

1. **Authentication**: JWT-based with refresh tokens
2. **Password Encryption**: BCrypt with strength 12
3. **CSRF Protection**: Cookie-based CSRF tokens
4. **SQL Injection Prevention**: JPA parameterized queries
5. **XSS Protection**: Content Security Policy (CSP) + input sanitization filter
6. **Secure Headers**: HSTS, X-Frame-Options, etc.
7. **Rate Limiting**: Bucket4j, per-endpoint buckets (stricter for auth/search/AI chat)
8. **Account Security**: Account lockout after failed attempts
9. **Session Management**: Redis-based stateless sessions
10. **Input Validation**: Jakarta Validation
11. **Tenant isolation**: Hibernate `@Filter` (read-scoping) + `TenantGuard` service-layer check on every write (defense in depth against cross-tenant IDOR)

## 📦 Key Features

### Multi-tenant SaaS core
- ✅ Store registration/onboarding, staff invitations, per-store subscription plans (FREE_TRIAL / BASIC / PRO) billed via PayPal
- ✅ Public storefront per store (`/store/{slug}`) + owner dashboard, fully data-isolated per tenant

### Product Management
- ✅ Generic, industry-agnostic product model — free-named attributes (not hardcoded to fashion's size/color), works for retail, F&B, beauty, hospitality alike
- ✅ Multiple images per product (Cloudinary)
- ✅ Variant generation (color × size × ... — any axes the store defines)
- ✅ Bulk import/export via Excel
- ✅ Category tree, filtering, pagination, reviews & ratings, loyalty points

### Selling
- ✅ POS terminal (in-store checkout, `sale/` package)
- ✅ Storefront cart + checkout, order tracking, coupons
- ✅ Purchase orders & supplier management (`purchaseorder/`, `supplier/`)
- ✅ GHN shipping integration

### Payments
- ✅ PayPal (checkout, refunds, subscription billing)
- ✅ MoMo, SePay (VietQR bank transfer via webhook)

### AI — storefront chatbot
- ✅ Customer-facing chat widget on every storefront page
- ✅ Answers using live tool-calls against the store's own products/categories/policies (real-time — no stale cache/index)
- ✅ Gemini primary, automatic Groq fallback on any upstream failure
- ✅ Store owners write their own free-named policies (return/shipping/warranty/...) the assistant is grounded on

### User Management
- ✅ Registration, email OTP verification, login, TOTP 2FA
- ✅ Store-scoped roles (OWNER/MANAGER/STAFF) + platform SUPER_ADMIN
- ✅ Multiple shipping addresses, order history, wishlist

## 🚀 Getting Started

### Prerequisites
- Java 17
- Node.js 20+
- Docker & Docker Compose (for local Postgres/Redis/Kafka)
- (Maven not required — the repo ships `./mvnw`)

### Environment Variables

Copy the example files and fill in your own values (never commit real secrets):

```bash
# Backend
cp backend/.env.example backend/.env

# Frontend
cp frontend/.env.example frontend/.env.local
```

See [backend/.env.example](backend/.env.example) for the full list of required
variables (database, JWT, Cloudinary, PayPal, MoMo, SePay, GHN, Brevo email,
Gemini/Groq AI keys) with notes on where to obtain each one.

### Running Locally (recommended for development)

#### 1. Start infrastructure only (Postgres, Redis, Kafka, Zookeeper):
```bash
cd backend
docker-compose up -d postgres redis kafka zookeeper
```

#### 2. Run the backend with the `dev` profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
Backend API: http://localhost:8080/api

#### 3. Run the frontend:
```bash
cd frontend
npm install
npm start   # ng serve — proxies /api to localhost:8080 via proxy.conf.json
```
Frontend: http://localhost:4200

### Running everything in Docker

```bash
cd backend
docker-compose up -d
```
Brings up Postgres, Redis, Kafka+Zookeeper, and the Spring Boot app itself (`staging` profile, 3 replicas) — closer to the production topology. There's also `docker-compose.prod.yaml` for a self-hosted production deploy (`prod` profile, required secrets enforced via `${VAR:?...}`).

### Port Configuration

| Service | Docker port | Local (dev profile) | Purpose |
|---|---|---|---|
| Spring Boot | 8081 | 8080 (context-path `/api`) | Backend API |
| PostgreSQL | 5433→5432 | 5433 | Database |
| Redis | 6380→6379 | 6380 | Cache & sessions |
| Kafka | 9092, 9093 | 9092, 9093 | Async messaging |
| Angular (`ng serve`) | - | 4200 | Frontend |

## 📁 Project Structure

```
.
├── backend/
│   ├── src/main/java/com/ut/edu/backend/   # Package-by-feature (modular monolith)
│   │   ├── store/            # Store (tenant), Subscription, staff invites, tenant resolution/filter
│   │   ├── product/          # Product (generic multi-industry) + images + import/export + view history
│   │   ├── category/         # Category tree
│   │   ├── policy/           # Free-named store policies (read by the AI chatbot)
│   │   ├── ai/                # Storefront chatbot: Gemini/Groq providers, tool-calling, chat session (Redis)
│   │   ├── cart/             # Cart + Redis cart cache
│   │   ├── order/            # Orders + admin order management
│   │   ├── sale/             # POS (in-store) sales + customers
│   │   ├── purchaseorder/    # Purchase orders (stock-in from suppliers)
│   │   ├── supplier/         # Suppliers
│   │   ├── payment/          # PayPal + MoMo + SePay
│   │   ├── shipping/ghn/     # GHN (Giao Hàng Nhanh) shipping integration
│   │   ├── coupon/           # Coupons + usage tracking
│   │   ├── review/           # Reviews + review images
│   │   ├── wishlist/         # Wishlist
│   │   ├── user/             # Users, addresses (legacy global Role enum, unused for authorization)
│   │   ├── auth/             # Login, OTP, 2FA (TOTP), sessions, tokens
│   │   ├── dashboard/        # Owner dashboard aggregations
│   │   ├── email/            # Brevo → Gmail SMTP fallback chain
│   │   ├── media/            # Cloudinary image upload
│   │   ├── kafka/            # Kafka producers/consumers
│   │   ├── security/         # JWT filter, rate limiting, XSS filter
│   │   ├── config/           # Cross-cutting Spring config
│   │   ├── exception/        # Global exception handling
│   │   ├── validation/       # Custom Jakarta validators
│   │   └── common/           # BaseEntity + shared utilities
│   ├── src/main/resources/
│   │   ├── db/migration/                  # Flyway migrations (V1__baseline.sql, ...)
│   │   ├── application.properties         # Base config
│   │   ├── application-dev.properties     # Local development
│   │   ├── application-staging.properties # Docker (all-in-container)
│   │   └── application-prod.properties    # Render production
│   ├── Dockerfile
│   ├── docker-compose.yaml       # Local/staging stack
│   ├── docker-compose.prod.yaml  # Self-hosted production stack
│   └── pom.xml
│
├── frontend/
│   ├── src/app/
│   │   ├── features/        # Route-level feature modules: landing, login, store-register,
│   │   │                    #   storefront (+ storefront/chat), dashboard (products, POS, suppliers,
│   │   │                    #   purchase orders, policies, delivery partners...)
│   │   ├── core/             # Cross-cutting services (auth, cart, http, store profile...)
│   │   └── layout/           # Shared layout pieces
│   ├── src/environments/
│   └── package.json
│
└── README.md
```

## 🔧 Available Profiles

### Development (`dev`)
- Local backend port 8080, context-path `/api`
- Docker Postgres on 5433, Docker Redis on 6380
- Debug logging, Swagger UI, hot reload
- CORS: `localhost:3000`, `localhost:4200`

### Staging (`staging`, used by `docker-compose.yaml`)
- Fully containerized (Postgres, Redis, Kafka, backend all in Docker)
- Moderate logging, Swagger UI enabled

### Production (`prod`, Render)
- Neon (managed Postgres), Upstash (managed Redis), Aiven (managed Kafka, SASL_SSL)
- Swagger UI disabled, HTTPS enforced, security headers enforced, all secrets required via env vars

## 🗄️ Database Migrations (Flyway)

The schema is managed **exclusively by Flyway** (`backend/src/main/resources/db/migration`).
Hibernate only validates (`ddl-auto=validate`). Rules:

- Every schema change = a **new** file `V26__short_description.sql`, `V27__...` — never edit an applied migration, never run ad-hoc SQL on a managed database.
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

### Main API Endpoints (multi-tenant — scoped by JWT or by `{slug}`)

#### Authentication
- `POST /api/auth/register` / `POST /api/auth/login` / `POST /api/auth/refresh`
- `POST /api/auth/verify-otp`, `/api/auth/forgot-password`, `/api/auth/reset-password`

#### Store onboarding & public storefront
- `POST /api/stores/register` - register a new store (SaaS signup)
- `GET /api/stores/{slug}` / `/api/stores/{slug}/products` / `/api/stores/{slug}/categories`
- `POST /api/stores/{slug}/chat` - AI storefront chatbot

#### Owner dashboard (JWT-scoped to the caller's store, `OWNER`/`MANAGER`)
- `GET/POST/PUT/DELETE /api/store/products` (+ `/import`, `/variants`)
- `GET/POST/PUT/DELETE /api/store/policies` - store policies read by the AI chatbot
- `GET/POST/PUT/DELETE /api/store/suppliers`, `/api/store/purchase-orders`
- `/api/store/sales/**` - POS

#### Platform admin (`SUPER_ADMIN` only)
- `/api/platform/stores`, `/api/platform/**`

#### Payments & shipping
- `POST /api/payments/create`, `/api/payments/capture`, `/api/payments/webhook/paypal`
- `/api/payments/webhook/sepay`
- `GHN_*` shipping webhook (see `shipping/ghn`)

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
npm test   # ng test — Vitest + jsdom
```

## 🔑 Admin / Owner accounts

There's no separate global "admin" flag to flip. Access is store-scoped:

- Registering a store via `POST /api/stores/register` makes that user its `OWNER`.
- Staff are invited into a store (`StoreStaffController`) as `MANAGER` or `STAFF`.
- `SUPER_ADMIN` (platform operator, sees every store under `/api/platform/**`) is not self-service — set it directly on a user's role in the database.

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

## 🤖 Storefront AI Chatbot

A customer-facing chat widget on every storefront page (`ai/` + `policy/` packages):

- **Gemini** as the primary LLM, automatic fallback to **Groq** on any failure (rate limit, outage, ...) — both free tier
- **Tool-calling on live data**, not RAG/embeddings: `search_products`, `get_product_by_id`, `list_categories`, `get_store_policies` all query the current database at answer time, so a product edited or imported a second ago is already visible to the assistant
- **Store-defined policies**: owners write free-named policy entries (return/shipping/warranty/...) from the dashboard; the assistant answers policy questions grounded on that real text
- **Prompt-injection aware**: tool results are framed as untrusted data in the system prompt, not instructions; the product fields exposed to the model never include store-internal data (cost price, tax rate, internal notes)

## 📊 Monitoring

### Health Check
- http://localhost:8080/api/actuator/health (local dev)
- http://localhost:8081/api/actuator/health (docker)

### Metrics
- `/api/actuator/metrics`
- `/api/actuator/prometheus`

## 🚢 Deployment

Production runs on free-tier managed services, not the bundled docker-compose:

- **Backend**: Render (free tier) — `SPRING_PROFILES_ACTIVE=prod`, connected to Neon (Postgres), Upstash (Redis), and Aiven (Kafka, SASL_SSL)
- **Frontend**: Vercel (free tier) — Angular production build

For a fully self-hosted deployment instead, use `docker-compose.prod.yaml`:
```bash
export SPRING_PROFILES_ACTIVE=prod
export JWT_SECRET=your-production-secret
# ... other required env vars (see backend/.env.example)
docker-compose -f docker-compose.prod.yaml up -d
```

### SSL Configuration
HTTPS is terminated by the hosting platform (Render/Vercel) — no reverse proxy is run in this deployment.

## 📝 License

This project is licensed under the MIT License.

## 📧 Support

For issues and questions, please open an issue on GitHub.

---

Built with Spring Boot, Angular, and free-tier cloud infrastructure.
