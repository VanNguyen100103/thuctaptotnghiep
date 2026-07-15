# 🎯 TODO — Pivot sang SaaS quản lý bán hàng (kiểu KiotViet) + hoàn thiện để xin việc

> **Định vị mới**: Nền tảng SaaS multi-tenant cho cửa hàng thời trang — mỗi cửa hàng đăng ký, có storefront + dashboard + dữ liệu riêng, trả phí theo gói.
> **Mục tiêu**: Nhà tuyển dụng mở GitHub → thấy SaaS multi-tenant có test + CI + demo sống → khác biệt hẳn với các đồ án e-commerce thông thường.
> Làm đúng thứ tự. Phase 0 làm **ngay hôm nay**, trước mọi thứ khác.
> Tổng thời gian ước tính: **7–8 tuần** (đã gồm ~1 tuần thanh toán nội địa VN).

---

## 🔴 Phase 0 — KHẨN CẤP: Dọn secret (ngay hôm nay, ~2 giờ)

Repo đang public nên mọi key dưới đây coi như **đã bị lộ**. Xóa khỏi file là CHƯA đủ — key vẫn nằm trong git history.

### 0.1. Revoke / tạo lại toàn bộ key
- [ ] Cloudinary: Dashboard → Settings → Access Keys → regenerate API secret
- [ ] PayPal: developer.paypal.com → tạo lại client secret (sandbox)
- [ ] Gemini key số 1 (trong `application.properties`): aistudio.google.com → xóa, tạo mới
- [ ] Gemini key số 2 (trong `application-dev.properties`): xóa luôn
- [ ] DeepSeek: platform.deepseek.com → revoke key `sk-228e...`
- [ ] Gmail app password (cái đã hardcode trong properties): myaccount.google.com → Security → App passwords → xóa, tạo mới
- [ ] Đổi mật khẩu PostgreSQL cũ nếu có dùng ở đâu ngoài local
- [ ] **Redis Cloud** (phát hiện 10/7: endpoint + password từng hardcode trong `application-prod.properties` trên GitHub) → đã thay bằng Upstash, vào console Redis Cloud **xóa database cũ** cho dứt điểm

### 0.2. Xóa secret khỏi code — ✅ XONG (2026-07-10)
- [x] `README.md` — phần Environment Variables → trỏ sang `.env.example`
- [x] `application.properties` — mọi default thật → rỗng; JWT default đánh dấu dev-only
- [x] `application-dev.properties` — Gemini key, Gmail password, DB password → env vars
- [x] `application-staging.properties` — DB password, email, DeepSeek → dọn sạch
- [x] `application-prod.properties` — dọn default email cá nhân
- [x] `backend/docker-compose.yaml` — toàn bộ secret defaults → `${VAR:-}`; thêm `name: ecommerce-dev`
- [x] `backend/docker-compose.prod.yaml` — DB_PASSWORD/JWT_SECRET bắt buộc (`:?`), còn lại `${VAR:-}`; thêm `name: ecommerce-prod`

### 0.3. Chuẩn hóa config + dọn repo — ✅ XONG (2026-07-10)
- [x] Tạo `backend/.env.example` và `frontend/.env.example` (chỉ tên biến + hướng dẫn lấy key)
- [x] Docker-compose đọc từ `.env` qua `${VAR}` (compose tự đọc `.env` cùng thư mục)
- [x] Thêm `.gitignore` root; bổ sung `.env`/`*.log` vào `backend/.gitignore`; `!.env.example` vào `frontend/.gitignore`
- [x] `git rm --cached backend/app.log` + xóa file
- [ ] Xóa `backend/HELP.md` (file rác Spring Initializr — đang bị chính backend/.gitignore ignore nhưng vẫn nằm trên disk)

### 0.4. Làm sạch git history
- [ ] **Khuyến nghị**: tạo repo mới (đặt tên theo sản phẩm SaaS, vd `fashionhub-saas`), squash thành 1 commit init sạch — tiện thể đúng lúc pivot, repo mới = câu chuyện mới
- [ ] Bật GitHub Secret Scanning + Push Protection (Settings → Code security)
- [ ] Từ giờ: làm việc theo branch + PR, commit theo Conventional Commits (`feat:`, `fix:`, `test:`...)

---

## 🏗️ Phase 1 — SaaS Core: Multi-tenancy backend (~1.5 tuần)

Mô hình chọn: **Shared schema + tenant discriminator (`store_id`)** — chuẩn cho SaaS vừa và nhỏ, dễ vận hành trên free tier, và là câu chuyện kiến trúc tốt khi phỏng vấn (biết trade-off so với schema-per-tenant).

### 1.0. Flyway migration — ✅ XONG (2026-07-14)
- [x] Thêm `flyway-core` + `flyway-database-postgresql` vào `pom.xml`
- [x] `V1__baseline.sql` (24 bảng, dump từ schema Hibernate tạo) trong `backend/src/main/resources/db/migration`
- [x] `ddl-auto` → `validate` ở cả 4 profile; `baseline-on-migrate=true` nên DB có sẵn (Neon prod, dev cũ) tự baseline và bỏ qua V1, DB trống chạy V1 từ đầu
- Từ giờ **mọi thay đổi schema viết thành migration mới** (`V2__...`, `V3__...`) — Hibernate không tự sửa bảng nữa. Migration `store_id` ở 1.2 chính là `V2`

### 1.1. Entity mới — ✅ XONG (2026-07-14, package `store/`)
- [x] `Store` (tenant): `id`, `name`, `slug` (unique — dùng cho URL storefront), `logoUrl`, `phone`, `address`, `status` (TRIAL / ACTIVE / SUSPENDED), `createdAt`
- [x] `Subscription`: `id`, `store_id`, `plan` (FREE_TRIAL / BASIC / PRO), `status` (ACTIVE / EXPIRED / CANCELLED), `startDate`, `endDate`, `paypalSubscriptionId` + `StoreRepository`, `SubscriptionRepository`
- [x] Enum `StoreRole`: OWNER, MANAGER, STAFF (theo store) + SUPER_ADMIN (toàn hệ thống — người vận hành SaaS)
- [x] `User` thêm: `store_id` (nullable — khách mua hàng thì null, nhân viên/chủ shop thì có) + `storeRole`
- [x] Migration `V2__create_stores_and_subscriptions.sql` (kèm 1 store demo `fashion-store-demo` để 1.2 backfill)
- [x] **Neon đã đồng bộ Flyway (2026-07-14)**: V2+V3 áp tay qua SQL Editor → đã baseline `flyway_schema_history` tại version 3 + chạy V4 qua Flyway CLI (Docker). Từ giờ deploy code mới Flyway tự áp V5+ — không chạy SQL tay trên Neon nữa

### 1.2. Thêm `store_id` vào entity nghiệp vụ — ✅ XONG (2026-07-14)
- [x] Trực tiếp: `Product`, `Category`, `Order`, `Cart`, `Coupon`, `CouponUsage`, `Payment`, `Wishlist`, `ProductView` (`@ManyToOne Store` + `@JsonIgnore`)
- [x] Gián tiếp qua quan hệ (không cần cột riêng): `ProductImage`, `OrderItem`, `CartItem`, `Review`, `ReviewImage`
- [x] Không đụng: `Address`, `OtpVerification`, `TwoFactorAuth`, `UserSession`, `VerificationToken`
- [x] Migration `V3__add_store_id_to_business_tables.sql` (V2 đã dùng cho stores/subscriptions): cột `store_id` + FK + index cho 9 bảng, backfill toàn bộ dữ liệu cũ về store demo `fashion-store-demo`
- [x] Unique global → unique theo store: `products.slug/sku`, `categories.name/slug`, `coupons.code`, `carts.user_id` → `(store_id, ...)` — 2 cửa hàng được trùng slug/mã coupon
- Lưu ý: `store_id` tạm **nullable** vì service chưa set store khi ghi; sau khi 1.4 xong sẽ có `V4` siết NOT NULL. `Cart` vẫn `@OneToOne` với User — đổi sang `@ManyToOne` (1 cart/user/store) trong 1.4

### 1.3. Cơ chế tenant isolation (phần quan trọng nhất) — ✅ XONG phần backend core (2026-07-14)
- [x] `TenantContext`: ThreadLocal giữ `storeId` (package `store/`)
- [x] JWT thêm claim `storeId` + `storeRole` khi login (chỉ khi user thuộc store); `UserPrincipal` mang `storeId/storeRole`; `TenantResolverFilter` (sau `JwtAuthenticationFilter`) set vào `TenantContext`, clear trong `finally`
- [x] Storefront public: `TenantResolverFilter` resolve slug từ URL `/stores/{slug}/**` (slug ưu tiên hơn JWT; store SUSPENDED → không resolve)
- [x] Enforce **kết hợp cả 2**: Hibernate `@Filter(tenantFilter)` trên 9 entity, bật qua `TenantFilterAspect` (AOP trước mọi repository call, cần `spring-boot-starter-aop`); repository tường minh `findByStoreIdAnd...` bổ sung dần khi refactor 1.4
- [x] Service double-check: `TenantGuard.requireSameStore(...)` (trả 404 để không lộ resource store khác) — sẵn sàng, **áp vào từng service khi làm 1.4** (hiện chưa có endpoint scoped theo tenant)
- [x] Cache Redis: cart key → `cart:{storeId}:{userId}` (0 = chưa gắn tenant); Elasticsearch: **code ES chưa tồn tại trong repo** → thêm field `storeId` khi tích hợp ES thật
- Test: 16/16 xanh (`./mvnw test`), gồm test JWT tenant claim mới + integration test Testcontainers chạy đủ V1→V2→V3 + Hibernate validate

### 1.4. Refactor controller hiện có — ✅ XONG (2026-07-14)
- [x] `Admin*Controller` → `/api/store/**`: products, orders, dashboard (yêu cầu OWNER/MANAGER; mọi `findById` đi qua helper `findStoreProduct/Order/...` chống IDOR; list/stats tự scope qua Hibernate filter; 2 native search query nhận `storeId` tường minh vì native bypass filter). Delete/bulk-price chỉ OWNER
- [x] `/api/platform/**` cho SUPER_ADMIN: `PlatformStoreController` (list stores, PATCH `{id}/status` suspend/reactivate, stats toàn hệ thống) + `AdminController` cũ → `/platform/users`. `StoreRole` → authority `ROLE_OWNER/MANAGER/STAFF/SUPER_ADMIN` trong `UserPrincipal`
- [x] Storefront public: `StorefrontController` — `/api/stores/{slug}` (info), `/products`, `/products/{id}`, `/categories` (permitAll GET; slug resolve tenant qua `TenantResolverFilter`, store SUSPENDED → 404)
- [x] Cart/Order/Review/Wishlist/ProductView/Payment/CouponUsage của khách: gắn store khi tạo — derive từ product/cart/order (không phụ thuộc URL, route cũ vẫn đúng). Review scoped qua product (`findByProductStoreId` cho trang moderation)
- [x] Controller mixed (`Category/Coupon/Product/Review/Payment-refund`): `hasRole('ADMIN')` → `hasAnyRole('OWNER','MANAGER')` + set store khi tạo + guard mọi write theo tenant; `AIController` → SUPER_ADMIN
- [x] Migration `V4__assign_legacy_admins_to_demo_store.sql`: user ADMIN cũ → OWNER store demo (giữ quyền vào dashboard sau khi bỏ check ROLE_ADMIN)
- Lưu ý: frontend hiện gọi `/admin/**` sẽ 404 — cập nhật path mới (`/store/**`) trong Phase 3; route cũ `/products`, `/categories`, `/cart`... của khách vẫn hoạt động như trước

---

## 💳 Phase 2 — SaaS Business: Onboarding + Subscription (~1 tuần)

### 2.1. Onboarding cửa hàng
- [ ] API `POST /api/stores/register`: đăng ký user + tạo store + gán OWNER + tạo Subscription FREE_TRIAL 14 ngày — tất cả trong 1 transaction
- [ ] Auto-seed dữ liệu mẫu cho store mới (vài category + sản phẩm demo) để dashboard không trống
- [ ] Owner mời nhân viên qua email (tái dụng hệ thống mail + VerificationToken sẵn có)

### 2.2. Subscription & gating
- [ ] Định nghĩa gói: FREE_TRIAL (14 ngày, full tính năng) / BASIC (giới hạn vd 50 sản phẩm, 1 nhân viên) / PRO (không giới hạn + AI recommendations + Elasticsearch)
- [ ] PayPal Subscriptions (sandbox): tạo plan, subscribe, webhook cập nhật trạng thái (tái dụng PaymentService + webhook sẵn có)
- [ ] `SubscriptionGuard` (interceptor hoặc annotation `@RequiresPlan(PRO)`): chặn tính năng theo gói, store hết hạn → chỉ đọc, không ghi
- [ ] Scheduled job (`@Scheduled`): quét subscription hết hạn hằng ngày → chuyển status, gửi mail nhắc (tái dụng SendGrid/Gmail sẵn có)

### 2.3. Dashboard số liệu cho chủ shop
- [ ] API báo cáo theo store: doanh thu theo ngày/tháng, top sản phẩm, đơn theo trạng thái, tồn kho thấp (tái dụng AdminDashboardController)

### 2.4. Thanh toán nội địa VN cho storefront (~1 tuần)

Phân vai rõ: **PayPal = subscription SaaS của chủ shop** (giữ nguyên); **thanh toán nội địa = khách mua hàng trên storefront**. Enum `PaymentMethod` đã có sẵn `BANK_TRANSFER`, `CASH_ON_DELIVERY` — giờ implement thật.

- [ ] **Refactor Strategy pattern trước** (điểm kiến trúc ăn điểm phỏng vấn): interface `PaymentProvider` (`createPayment`, `verifyCallback`, `refund`) → `PayPalService` hiện có implement lại theo interface, các provider mới cắm vào — thêm cổng mới không sửa code cũ (Open/Closed)
- [ ] **VNPay** (ưu tiên 1 — cổng phổ biến nhất, sandbox free tại sandbox.vnpayment.vn): thẻ ATM/QR/internet banking. Kỹ thuật: ký HMAC-SHA512, redirect return URL + IPN callback. Nhà tuyển dụng VN nhìn là biết
- [ ] **MoMo** (ưu tiên 2 — ví điện tử phổ biến nhất, test env tại developers.momo.vn): ký HMAC-SHA256, IPN webhook
- [ ] **VietQR chuyển khoản ngân hàng qua PayOS** (payos.vn, free — đúng trải nghiệm "quét QR app ngân hàng" người Việt dùng hằng ngày): tạo QR động theo đơn, webhook tự xác nhận khi tiền vào. Đơn giản hơn: SePay, hoặc chỉ hiện QR tĩnh vietqr.io + xác nhận thủ công (kém hơn)
- [ ] **COD**: không cần bên thứ 3 — tạo đơn trạng thái `PENDING_COD`, chủ shop xác nhận khi giao
- [ ] (Tùy chọn) ZaloPay nếu còn thời gian
- [ ] **Webhook idempotent**: mỗi IPN có thể bắn nhiều lần → check `transactionRef` đã xử lý chưa trước khi cộng tiền/đổi trạng thái (điểm nói trong phỏng vấn)
- [ ] `Payment` entity: thêm `gatewayTransactionId`, `gatewayResponse` (JSON), dùng đúng enum `PaymentMethod` sẵn có
- [ ] Frontend checkout: UI chọn phương thức (VNPay / MoMo / Chuyển khoản QR / COD / PayPal), trang kết quả thanh toán, hiển thị QR VietQR
- [ ] Test: unit test cho verify chữ ký từng cổng + integration test webhook idempotency
- [ ] Ghi vào README tài khoản test sandbox từng cổng (VNPay có sẵn thẻ test NCB công khai trong docs)

---

## 🖥️ Phase 3 — Frontend SaaS (Next.js) (~1 tuần)

- [ ] **Landing page SaaS** (trang chủ mới): giới thiệu sản phẩm, bảng giá 3 gói, nút "Dùng thử miễn phí" — đây là bộ mặt của "sản phẩm", nhà tuyển dụng thấy đầu tiên
- [ ] **Trang đăng ký cửa hàng**: form tạo store (tên, slug, logo) → vào thẳng dashboard
- [ ] **Dashboard chủ shop** (`/dashboard`): refactor từ trang admin hiện tại — quản lý sản phẩm, đơn hàng, coupon, nhân viên, báo cáo doanh thu (chart), trang billing/nâng cấp gói
- [ ] **Storefront theo store** (`/store/[slug]`): refactor storefront hiện tại thành dynamic route — mỗi cửa hàng có trang riêng với logo/tên riêng
  - Lưu ý: subdomain wildcard (`shop1.domain.com`) cần custom domain trên Vercel; bản demo dùng path-based `/store/[slug]` là đủ, ghi rõ trong README hướng nâng cấp subdomain
- [ ] **Trang platform admin** (`/platform`): SUPER_ADMIN xem danh sách store, suspend, thống kê
- [ ] Cập nhật auth context: lưu `storeId` + `storeRole`, điều hướng theo role sau login

---

## 🧪 Phase 4 — Testing (song song từ Phase 1, chốt ~1 tuần)

### 4.0. Nền tảng test — ✅ XONG (2026-07-14)
- [x] Testcontainers (`spring-boot-testcontainers`, `junit-jupiter`, `postgresql`) + `application-test.properties` (kafka off)
- [x] JaCoCo qua Maven profile `coverage` (tự bật khi JDK ≤ 24 — JaCoCo chưa hỗ trợ JDK 26 local; CI JDK 17 sẽ có report)
- [x] Mockito gắn `-javaagent` tường minh trong surefire (JDK 21+ chặn self-attach)
- [x] Test đầu tiên: `OtpServiceTest` (7 case), `JwtTokenProviderTest` (6 case), `BackendApplicationTests` = integration test Testcontainers boot full context + Flyway migrate DB trống — `./mvnw test` xanh 14/14
- Lưu ý máy local: Docker Engine 29 yêu cầu API ≥ 1.40 nên đã tạo `~/.docker-java.properties` (`api.version=1.44`) để Testcontainers kết nối được — config theo máy, không nằm trong repo

### 4.1. Backend unit tests (JUnit 5 + Mockito — sẵn trong `spring-boot-starter-test`)
- [ ] `StoreServiceTest` — đăng ký store (slug trùng), trial tự tạo
- [ ] `SubscriptionServiceTest` — gating theo gói, hết hạn, webhook cập nhật
- [ ] `AuthServiceTest` — login gắn đúng `storeId` vào JWT, account lockout
- [ ] `OrderServiceTest`, `CartServiceTest`, `ProductServiceTest` — nghiệp vụ + scope theo store
- [ ] Mục tiêu coverage tầng service ≥ 70% (JaCoCo, thêm plugin vào `pom.xml`)

### 4.2. Integration tests (Testcontainers — điểm nhấn CV)
- [ ] Thêm `org.testcontainers:postgresql` + `junit-jupiter`; tạo `application-test.properties` (tắt Kafka/ES)
- [ ] **Test tenant isolation (quan trọng nhất)**: tạo store A + store B → user store A gọi API đọc/sửa dữ liệu store B → phải 403/404. Đây là integration test thuyết phục nhất toàn dự án
- [ ] Test luồng onboarding: register store → login → tạo sản phẩm → xuất hiện đúng ở `/api/stores/{slug}/products`
- [ ] Test subscription gating: store BASIC tạo sản phẩm thứ 51 → bị chặn
- [ ] Test security: `/api/platform/**` với role OWNER → 403; không token → 401
- [ ] Đảm bảo `./mvnw verify` xanh hoàn toàn

### 4.3. Frontend tests
- [ ] Cài Vitest + React Testing Library; thêm script `"test"` + `"typecheck": "tsc --noEmit"` vào `package.json`
- [ ] Test: form đăng ký store (validation), Cart (tính tổng), ProductCard, bảng giá gói
- [ ] `npm run lint` pass sạch
- [ ] (Cộng điểm lớn) Playwright E2E: đăng ký store → tạo sản phẩm trong dashboard → mở storefront thấy sản phẩm → khách mua hàng

---

## ⚙️ Phase 5 — CI/CD với GitHub Actions (~1 ngày)

- [ ] `.github/workflows/ci.yml`:
  - Job backend: JDK 17 → `./mvnw verify` (Testcontainers chạy được trên GitHub Actions vì có sẵn Docker)
  - Job frontend: Node 20 → `npm ci` → `lint` → `typecheck` → `test` → `build`
  - Trigger: push + pull_request vào `main`
- [ ] Badge CI + coverage vào đầu README
- [ ] Branch protection cho `main`: bắt buộc PR + CI xanh
- [ ] (Tùy chọn) CD: merge main → Render Deploy Hook; Vercel tự deploy sẵn

---

## 🚀 Phase 6 — Deploy "vĩnh viễn" (~2-3 ngày)

**Chiến lược 2 bước đã chốt** (đã tốt nghiệp nên không dùng được GitHub Student Pack):
- **Bước 1 — ngay bây giờ (0đ)**: Vercel + Render free + Neon + Upstash + UptimeRobot ping. Kafka/ES tắt qua feature flag (đã có `spring.kafka.enabled`), full stack chạy Docker local — **ghi rõ trong README đây là quyết định kiến trúc có chủ đích**. Song song: thử đăng ký Oracle Cloud Always Free (2 OCPU/12GB ARM, home region Singapore) — được thì thay Render, chạy full stack 0đ.
- **Bước 2 — khi bắt đầu rải CV**: thuê VPS 4GB (~110–150k/tháng: Hetzner CAX11 ARM hoặc nhà cung cấp VN) → chạy trọn `docker-compose.prod.yaml` (cả Kafka + Elasticsearch) + Nginx + SSL Let's Encrypt + GitHub Actions deploy qua SSH. Demo full stack không sleep + kỹ năng DevOps thật là điểm cộng lớn nhất khi phỏng vấn. Chi phí 3–4 tháng xin việc ~400–600k.

### 6.0. Tái cấu trúc Docker Compose (làm trước khi lên VPS)
- [ ] Chuyển compose từ `backend/` lên **root repo**, thêm service **frontend** (Next.js) — 1 lệnh `docker compose up` chạy cả app; nginx route `/` → frontend, `/api` → backend
- [ ] Chuyển sang mô hình **base + override**: `docker-compose.yaml` (chung) + `docker-compose.override.yaml` (dev: mở port, 1 replica) + `docker-compose.prod.yaml` (chỉ khác biệt: restart, không mở port hạ tầng) — hết cảnh 2 file trùng 90% và lệch nhau
- [ ] **Prod: đóng toàn bộ cổng hạ tầng** — bỏ `ports:` của Postgres/Redis/Kafka/Zookeeper (Redis đang không có password mà mở cổng ra ngoài!), chỉ nginx mở 80/443; đặt password cho Redis (`requirepass`)
- [ ] Xóa secret khỏi defaults `${VAR:-key_thật}` → `${VAR:?err}` (bắt buộc) hoặc `${VAR:-}` + dùng `env_file: .env` (lưu ý: file prod còn lộ thêm PayPal Client ID)
- [ ] Thêm service **Elasticsearch** vào compose (README đang ghi có nhưng file không có) hoặc sửa README
- [ ] Bật lại healthcheck backend: `permitAll()` cho `/actuator/health` + nginx `depends_on: condition: service_healthy`
- [ ] Giảm `replicas: 3` → 1 cho dev; prod scale theo RAM thật của VPS
- [ ] (Nice-to-have) Kafka chuyển KRaft mode — bỏ được container Zookeeper
- [ ] Sửa lệch cổng Kafka dev: app chạy trên host phải dùng `localhost:9093` (listener PLAINTEXT_HOST) — `application-dev.properties` đang trỏ `localhost:9092` nên KafkaAdmin báo "Could not configure topics" khi chạy backend ngoài Docker

### Bước 1 — hạ tầng 0đ
- [ ] **Frontend → Vercel**: free vĩnh viễn, không sleep, auto deploy
- [ ] **Database → Neon.tech** (Postgres free không hết hạn — KHÔNG dùng Supabase vì pause sau 1 tuần không hoạt động)
- [ ] **Redis → Upstash** (free tier vĩnh viễn)
- [x] **Backend → Render** (đã tối ưu 512MB) + UptimeRobot ping `/api/actuator/health` mỗi 5 phút chống sleep (xong 15/07/2026 — status page: https://stats.uptimerobot.com/8QPXy2LFrm)
- [ ] Elasticsearch: tắt trên demo, fallback search Postgres full-text; Kafka: tắt, xử lý đồng bộ
- [ ] **Email: thay SendGrid bằng Brevo** (SendGrid đã bỏ gói free từ 7/2025, trial 60 ngày rồi $19.95/tháng; Brevo free 300 email/ngày vĩnh viễn, không cần domain riêng)
  - [x] Code đã xong: `BrevoEmailService` + chuỗi fallback Brevo → SendGrid → SMTP → log trong `EmailServiceImpl` và `KafkaConsumerService`
  - [ ] Tạo tài khoản Brevo (brevo.com) → lấy API key (Settings → SMTP & API → API Keys)
  - [ ] Verify sender email trong Brevo (Settings → Senders) — phải trùng với `spring.mail.from`
  - [ ] Set biến môi trường `BREVO_API_KEY` trên Render (Environment tab) và trong `.env` local
  - [ ] Test: đăng ký tài khoản mới trên demo → nhận được email OTP
  - Lưu ý: Gmail SMTP không bao giờ chạy được trên Render vì Render chặn cổng SMTP outbound — SMTP fallback chỉ có tác dụng khi chạy local/VPS
- [ ] Thử đăng ký Oracle Always Free (cần thẻ Visa/Mastercard để verify, không trừ tiền) — thử tạo VM vài lần nếu báo "out of capacity"

### Bước 2 — VPS khi rải CV (checklist chi tiết sẽ bổ sung khi đến lúc)
- [ ] Thuê VPS 4GB (Hetzner CAX11 ~€3.8/tháng hoặc VN provider), Ubuntu LTS
- [ ] SSH key-only + UFW firewall + fail2ban
- [ ] Cài Docker + chạy `docker-compose.prod.yaml` full stack
- [ ] Nginx reverse proxy + SSL Let's Encrypt (certbot)
- [ ] GitHub Actions: merge main → SSH deploy tự động
- [ ] Trỏ domain (nếu có) hoặc dùng IP + subdomain free (vd DuckDNS)
- [ ] **Seed demo cho nhà tuyển dụng**: 2 cửa hàng mẫu có sản phẩm + ảnh thật, tài khoản ghi trong README:
  - Chủ shop: `owner@demo.com / Owner@123` → vào dashboard
  - Khách: `customer@demo.com / Customer@123` → mua hàng trên storefront
  - Platform admin: `admin@demo.com / Admin@123` → trang quản trị hệ thống
- [ ] PayPal sandbox subscribe được end-to-end (ghi tài khoản sandbox test trong README)
- [ ] CORS prod trỏ đúng domain Vercel
- [ ] (Tùy chọn) Domain riêng ~$3/năm → mở đường subdomain-per-store

---

## 📖 Phase 7 — README "gộp toàn bộ kỹ thuật" (~2 ngày)

Cấu trúc README mới (viết như trang giới thiệu sản phẩm SaaS):

- [ ] **Đầu trang**: tên sản phẩm + 1 câu pitch ("Multi-tenant SaaS platform for fashion retailers") + badges (CI, coverage, license) + **🔗 LIVE DEMO + 3 tài khoản demo** ngay dòng thứ 3
- [ ] **Screenshots/GIF**: landing page, dashboard, storefront 2 store khác nhau (chứng minh multi-tenant bằng hình ảnh!), trang billing
- [ ] **Sơ đồ kiến trúc Mermaid**: Next.js → Nginx → Spring Boot → Postgres/Redis/Kafka/ES + luồng TenantContext
- [ ] **Sơ đồ ERD Mermaid**: nhấn mạnh `Store` là gốc, các bảng gắn `store_id`
- [ ] **Bảng kỹ thuật + lý do + link code** (phần "gộp kỹ thuật" ăn điểm nhất):
  | Kỹ thuật | Giải quyết bài toán gì | Code |
  |---|---|---|
  | Multi-tenancy (shared schema + Hibernate Filter) | Cô lập dữ liệu giữa các cửa hàng trên 1 DB | `TenantContext`, `security/` |
  | JWT + claim storeId | Stateless auth theo tenant | `security/` |
  | PayPal Subscriptions + webhook | Billing SaaS tự động theo gói | `SubscriptionService` |
  | VNPay / MoMo / VietQR (PayOS) / COD — Strategy pattern | Thanh toán nội địa VN, thêm cổng mới không sửa code cũ | `payment/` |
  | Redis (key prefix theo tenant) | Cache giỏ hàng đa cửa hàng | `CartService` |
  | Kafka | Xử lý đơn + email async | `kafka/` |
  | Elasticsearch (filter storeId) | Search trong phạm vi từng shop | `SearchService` |
  | Testcontainers | Integration test tenant isolation với DB thật | `src/test/` |
  | Bucket4j | Rate limiting chống brute-force | ... |
- [ ] **Technical Highlights** (4-5 đoạn — chính là kịch bản trả lời phỏng vấn):
  - Vì sao chọn shared-schema thay vì schema-per-tenant (trade-off chi phí/độ phức tạp/mức cô lập)
  - Chống rò dữ liệu chéo tenant: 2 lớp (Hibernate Filter + service check) + integration test chứng minh
  - Subscription gating + xử lý webhook idempotent
  - Tối ưu chạy 512MB RAM trên Render: SerialGC, giảm pool
- [ ] **Getting Started**: copy `.env.example` → `docker-compose up` → chạy được thật trong < 10 phút
- [ ] **Testing**: lệnh chạy, số test, coverage thật (bỏ hướng dẫn `npm run test` sai hiện tại)
- [ ] **Deployment**: sơ đồ Vercel + Render + Neon + Upstash, giải thích trade-off free tier
- [ ] Xóa placeholder: "Your Name" → tên thật + LinkedIn/email; bỏ "(to be created)"; thêm file `LICENSE` thật
- [ ] (Tùy chọn) Tài liệu tiếng Việt riêng: chỉ viết lại sau khi README hoàn thiện, nếu thấy cần (HUONGDAN.md cũ đã xóa)
- [ ] GitHub polish: Description + Website (demo link) + Topics (`saas`, `multi-tenant`, `spring-boot`, `nextjs`, `ecommerce`, `redis`, `kafka`, `docker`) + pin repo lên profile

---

## ✅ Nghiệm thu cuối — định nghĩa "hoàn hảo"

- [ ] Người lạ clone repo, theo README chạy được local trong < 10 phút
- [ ] Demo sống: đăng ký cửa hàng mới → tạo sản phẩm → mở storefront riêng → khách mua được — toàn bộ dưới 5 phút thao tác
- [ ] Đăng nhập store A không cách nào thấy dữ liệu store B (và có integration test chứng minh)
- [ ] `./mvnw verify` xanh, `npm test` xanh, CI badge xanh
- [ ] Không còn secret nào trong code lẫn git history
- [ ] README có: demo link, screenshots, sơ đồ kiến trúc + ERD, bảng kỹ thuật, Technical Highlights
- [ ] Lịch sử commit sạch, có PR
