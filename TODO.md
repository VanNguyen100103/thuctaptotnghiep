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

### 2.1. Onboarding cửa hàng — ✅ XONG (2026-07-15, package `store/`)
- [x] API `POST /api/stores/register`: đăng ký user + tạo store + gán OWNER + tạo Subscription FREE_TRIAL 14 ngày — tất cả trong 1 transaction (`StoreOnboardingService`; owner verify OTP qua `/auth/verify-otp` như đăng ký thường; slug reserved: `register`, `accept-invite`)
- [x] Auto-seed dữ liệu mẫu cho store mới (`StoreSampleDataSeeder`: 2 category + 4 sản phẩm demo) để dashboard không trống
- [x] Owner mời nhân viên qua email (`POST /store/staff/invite` OWNER-only → mail chứa link → `POST /stores/accept-invite` tạo tài khoản MANAGER/STAFF; entity `StaffInvitation` riêng + migration `V5` thay vì nhét vào `VerificationToken` vì token mời chưa có User để gắn FK; re-invite cùng email = refresh token cũ, không tạo dòng mới)
- Test: `StoreOnboardingServiceTest` (5) + `StoreStaffServiceTest` (8) — 28/28 unit test xanh

### 2.2. Subscription & gating
- [x] Định nghĩa gói: FREE_TRIAL (không giới hạn) / BASIC (50 sản phẩm, 1 nhân viên) / PRO (không giới hạn) — limit sống trong enum `SubscriptionPlan` (`maxProducts`/`maxStaff`, -1 = unlimited)
- [x] PayPal Subscriptions (sandbox): SDK cũ (`rest-api-sdk`, Payments API) không hỗ trợ Subscriptions API → gọi REST trực tiếp qua `RestTemplate` sẵn có (bean từ `AIConfiguration`) trong `payment/PayPalRestClient.java` (OAuth token, base URL sandbox/live). Tạo 1 lần qua curl (không phải code app): Product `PROD-9X537269YW0579156`, Plan BASIC `P-3AM31975VG418253JNKLNHQI` ($5/mo), Plan PRO `P-3RY4578284458611HNKLNHQQ` ($15/mo) — id lưu ở `PAYPAL_BASIC_PLAN_ID`/`PAYPAL_PRO_PLAN_ID`. `store/SubscriptionService.java`: `createSubscription`/`cancelSubscription` (chặn subscribe trùng khi đã có gói trả phí ACTIVE — tránh bị PayPal charge 2 lần) + `handleActivated`/`handleCancelled`/`handleExpired`/`handleRecurringPaymentSale` (idempotent theo `paypalSubscriptionId`, xác định store qua `custom_id` lúc tạo subscription, fallback gọi GET nếu thiếu). `StoreSubscriptionController`: `POST subscribe`/`cancel`, `GET /` (OWNER-only). Đồng thời fix luôn `PayPalService.verifyWebhookSignature` (trước đây là stub: sandbox luôn `true` không check thật, prod luôn `false` chưa implement) → gọi thật `POST /v1/notifications/verify-webhook-signature` với Webhook ID `8MM56701NU7005223` (đăng ký trên PayPal dashboard, event: `BILLING.SUBSCRIPTION.ACTIVATED/CANCELLED/EXPIRED` + `PAYMENT.SALE.COMPLETED`), dùng chung 1 webhook endpoint `/payments/webhook/paypal` với đơn hàng cũ. Thanh toán định kỳ (`PAYMENT.SALE.COMPLETED` có `billing_agreement_id`) chỉ log, không tạo `Payment` row (schema `Payment.order` bắt buộc, không có Order cho subscription — lịch sử thanh toán subscription là việc khác, chưa làm). Test: `SubscriptionServiceTest` (16) — 57/57 unit test xanh (trừ `BackendApplicationTests` cần Docker). **Đã verify end-to-end trên Render (01/09/2026)**: đăng ký store test → OTP → login → subscribe → duyệt bằng sandbox buyer account → webhook `BILLING.SUBSCRIPTION.ACTIVATED` tới, verify chữ ký pass, DB cập nhật đúng (`GET /store/subscription` trả về `plan:BASIC, status:ACTIVE`). Phát hiện phụ: `app.frontend.url`/`APP_FRONTEND_URL` trên Render đang trỏ `localhost:3000` thay vì domain Vercel thật → return_url PayPal và link mời nhân viên qua email đều đang sai trên production, cần sửa riêng.
- [x] `SubscriptionGuard` (`store/SubscriptionGuard.java`, gọi tường minh giống `TenantGuard` — không dùng interceptor/annotation vì chưa có endpoint premium thật sự cần gate, tránh code chết): `requireActiveSubscription`/`requireCanAddProduct`/`requireCanAddStaff`, ném `SubscriptionRequiredException` → 402 qua `GlobalExceptionHandler`. Đã gắn vào `StoreStaffService.invite()` (giới hạn nhân viên) và toàn bộ 9 method ghi của `AdminProductController` (giới hạn sản phẩm + chặn ghi khi hết hạn)
- [x] Scheduled job (`SubscriptionExpiryJob`, `@Scheduled` 1h sáng hằng ngày, cần `SchedulingConfig` bật `@EnableScheduling` — job đầu tiên trong repo): quét `findByStatusAndEndDateBefore(ACTIVE, today)` → chuyển `EXPIRED`, gửi mail qua `EmailService.sendSubscriptionExpiredEmail`. Test: `SubscriptionGuardTest` (8) + `SubscriptionExpiryJobTest` (4) + cập nhật `StoreStaffServiceTest` (+1) — 41/41 unit test xanh (không tính `BackendApplicationTests`, cần Docker chưa chạy được trong sandbox)

### 2.3. Dashboard số liệu cho chủ shop — ✅ XONG (02/09/2026)
- [x] API báo cáo theo store: doanh thu theo ngày/tháng, top sản phẩm, đơn theo trạng thái, tồn kho thấp (tái dụng `AdminDashboardController`) — hoá ra hầu hết đã có sẵn từ trước (`/store/dashboard/sales`, `/top-products`, `/order-status-stats`, `/revenue-pie-chart`, `/overview`), chỉ thiếu đúng phần "tồn kho thấp": trước đó `ProductRepository` chỉ có `countOutOfStock()` (đếm sản phẩm hết sạch, không phải danh sách cảnh báo sắp hết). Thêm `GET /store/dashboard/low-stock?threshold=10&limit=50` (query mới `findLowStock`, JPQL thường nên Hibernate tenant filter tự áp dụng như các query khác trong repo, không cần lọc `storeId` tay) trả danh sách sản phẩm active còn ≤ threshold, sắp theo tồn kho tăng dần.

### 2.4. Thanh toán nội địa VN cho storefront (~1 tuần)

Phân vai rõ: **PayPal = subscription SaaS của chủ shop** (giữ nguyên); **thanh toán nội địa = khách mua hàng trên storefront**. Enum `PaymentMethod` đã có sẵn `BANK_TRANSFER`, `CASH_ON_DELIVERY` — giờ implement thật.

- [x] **Refactor Strategy pattern trước** (điểm kiến trúc ăn điểm phỏng vấn) — (02/09/2026) interface `PaymentProvider` (`getMethod`, `createPayment`, `refund`) + `PaymentProviderRegistry` (Spring auto-collect `List<PaymentProvider>`, dispatch theo `PaymentMethod`); `PayPalPaymentProvider` là adapter mỏng bọc `PayPalService` **không sửa 1 dòng nào** của service cũ — chỉ `PaymentController.createPayment`/`refundPayment` được nối lại qua registry, `/execute` và PayPal webhook giữ nguyên
- [ ] **VNPay** (ưu tiên 1 — cổng phổ biến nhất). Kỹ thuật: ký HMAC-SHA512, redirect return URL + IPN callback
  - [x] Đăng ký sandbox tại `sandbox.vnpayment.vn/devreg/` (01/09/2026) — đang chờ email kích hoạt để lấy `vnp_TmnCode`/`vnp_HashSecret`
  - [ ] Code: `VNPayService` implement `PaymentProvider`
- [x] **MoMo** (ưu tiên 2 — ví điện tử phổ biến nhất). Kỹ thuật: ký HMAC-SHA256, IPN webhook
  - [x] **Không cần đăng ký merchant** — MoMo công khai sẵn bộ credentials test dùng chung trong docs chính thức (`developers.momo.vn/v3/vi/docs/payment/onboarding/test-instructions/`): `partnerCode=MOMO`, `accessKey=F8BBA842ECF85`, `secretKey=K951B6PE1waDMi640xX08PD3vg6EkVlz` (endpoint `test-payment.momo.vn`). Key dùng chung — phải đăng ký merchant riêng (`business.momo.vn`) nếu lên production
  - [x] Code (02/09/2026): `MomoPaymentProvider` implement `PaymentProvider` (`captureWallet`, gọi qua `RestTemplate` dùng chung, không tạo REST client riêng) + `MomoSignatureService` (ký/verify HMAC-SHA256, field order alphabet theo docs, verify độc lập bằng Python trước khi hardcode vào test) + `V6` migration mở CHECK constraint `payments.payment_method` cho `MOMO` + webhook `POST /payments/webhook/momo` (idempotent, tự trừ tồn kho vì MoMo không có bước capture như PayPal `/execute`, luôn trả `204`) + kích hoạt `PaymentMethodValidator` (trước đó là dead code, chưa từng được gọi) + sửa bug email xác nhận đơn hardcode cứng chữ "PayPal" bất kể phương thức thanh toán thật. 20 test mới (`MomoSignatureServiceTest`, `MomoPaymentProviderTest`, `PaymentProviderRegistryTest`, `PayPalPaymentProviderTest`) + 77/78 test cũ pass (1 lỗi còn lại là `BackendApplicationTests` cần Docker, môi trường sandbox không có, không liên quan thay đổi này). Chưa test end-to-end thật (cần Postgres cục bộ hoặc deploy) — còn lại: VNPay code (chờ email kích hoạt), COD, checkout UI frontend
- [x] ~~VietQR chuyển khoản ngân hàng qua PayOS~~ — **BỎ (01/09/2026)**: PayOS/Casso bắt buộc xác thực CCCD/mã số doanh nghiệp thật ngay từ bước đăng ký, không có sandbox giả lập như PayPal/VNPay/MoMo — không đáng đánh đổi cho project demo. VNPay + MoMo là đủ 2 cổng để demo Phase 2.4
- [x] **COD**: không cần bên thứ 3 — (02/09/2026) `CodPaymentProvider` implement `PaymentProvider` (không gọi gateway nào, `createPayment` trả thẳng `successUrl`). COD là đồng bộ (không có webhook/capture nào sẽ tới sau) nên `PaymentProvider` có thêm `default boolean confirmsImmediately()` (false với PayPal/MoMo, true với COD) — `PaymentController.createPayment` dựa vào cờ này để trừ tồn kho + xác nhận đơn NGAY trong request thay vì để `PAYMENT_PENDING` chờ mãi. Thêm `OrderStatus.PENDING_COD` (giống `PAID` về mặt tồn kho đã trừ, nhưng tiền mặt chưa thu — tách khỏi `PAID` vì `Payment.status` mới là nơi track tiền) + `V7` migration mở CHECK constraint `orders.status` + cập nhật state machine trong `OrderStatusValidator` (`PENDING → PENDING_COD → PROCESSING/CANCELLED/REFUNDED`, admin không tự set được, giống `FAILED`) + `Order.canBeCancelled()`. Chủ shop xác nhận thu tiền khi giao hàng tái dùng thẳng endpoint `PATCH /store/orders/{id}/status` có sẵn — khi chuyển `DELIVERED` mà `Payment` là COD và còn `PENDING` thì tự động `markAsPaid()` (không cần endpoint mới). Trừ tồn kho giờ dùng chung 1 helper `decrementStockForOrder` (trước đó lặp lại y hệt ở `/execute` và MoMo IPN — thêm bản thứ 3 cho COD là lúc nên gộp). 10 test mới (`CodPaymentProviderTest`, `OrderStatusValidatorTest` — trước đó chưa có test nào cho validator này) + 88 test tổng pass (trừ `BackendApplicationTests` cần Docker, không liên quan). Chưa test end-to-end thật
- [ ] (Tùy chọn) ZaloPay nếu còn thời gian
- [ ] **Webhook idempotent**: mỗi IPN có thể bắn nhiều lần → check `transactionRef` đã xử lý chưa trước khi cộng tiền/đổi trạng thái (điểm nói trong phỏng vấn)
- [ ] `Payment` entity: thêm `gatewayTransactionId`, `gatewayResponse` (JSON), dùng đúng enum `PaymentMethod` sẵn có
- [x] Frontend checkout: UI chọn phương thức (VNPay / MoMo / COD), trang kết quả thanh toán — (02/09/2026) làm cùng lúc với storefront/giỏ hàng Phase 3, xem chi tiết ở đó. Chọn được `PAYPAL`/`MOMO`/`CASH_ON_DELIVERY` (VNPay chưa code nên chưa có trong danh sách); `/payment/success` phân nhánh PayPal (gọi `/execute`) vs MoMo/COD (poll trạng thái, không gọi API xác nhận nào)
- [ ] Test: unit test cho verify chữ ký từng cổng + integration test webhook idempotency
- [ ] Ghi vào README tài khoản test sandbox từng cổng (VNPay có sẵn thẻ test NCB công khai trong docs)

---

## 🖥️ Phase 3 — Frontend SaaS (Angular) (~1 tuần)

> **Đổi kế hoạch (01/09/2026)**: chuyển từ Next.js sang Angular — dùng scaffold có sẵn `frontend-angular/` (tạo bằng `ng new`, chưa code gì, chưa commit) thay vì tạo mới Next.js project. Các mục dưới đây giữ nguyên nội dung, chỉ đổi công nghệ hiện thực.

- [x] **Nền tảng auth (01/09/2026)**: `AuthService` (Signals, JWT decode client-side vì backend không có endpoint trả storeId/storeRole — chỉ nằm trong JWT claims), `authGuard`/`guestGuard`, interceptor gắn Bearer token (401 khi đang đăng nhập mới logout, tránh trigger sai lúc login sai mật khẩu). Chưa có silent refresh token tự động — việc sau. Test: 33/33 (Vitest) + `ng build` sạch. Sửa kèm: `angular.json` thiếu `proxyConfig` (serve) và `fileReplacements` (environment.development.ts không hề được dùng) — cả 2 khiến `ng serve` gọi thẳng URL Render thay vì proxy local; CORS backend dev thiếu `localhost:4200`.
- [x] **Landing page SaaS**: bảng giá 3 gói dùng đúng số thật đã cấu hình trên PayPal (BASIC $5, PRO $15). **Redesign (02/09/2026)**: đổi từ layout căn giữa xám đơn giản sang hero 2 cột kiểu KiotViet (tiêu đề đậm bên trái + card "xem trước dashboard" giả bên phải: biểu đồ doanh thu + đơn hàng mới, tự vẽ bằng Tailwind chứ không sao chép ảnh thật của KiotViet) + tông màu chủ đạo xanh dương (`blue-600`, dùng thẳng palette Tailwind có sẵn, không tạo design token riêng) áp luôn vào header + 3 trang auth (login/verify-otp/store-register) cho đồng bộ toàn app
- [x] **Trang đăng ký cửa hàng** + **xác thực OTP** + **đăng nhập** — map lỗi `validationErrors` từ backend vào từng field, 3 dạng lỗi backend khác nhau xử lý qua `extractErrorMessage()`. **Redesign (02/09/2026)**: cả 3 trang chuyển sang layout split-screen kiểu modal đăng ký thật của KiotViet — panel gradient xanh bên trái (tiêu đề + mô tả riêng của app này, không copy nguyên văn KiotViet) + form trắng bên phải, không đổi field/logic form
- [ ] **Dashboard chủ shop** (`/dashboard`): đơn hàng, coupon, nhân viên, trang billing/nâng cấp gói vẫn chưa làm (các tab "Mua hàng/Đơn hàng/Khách hàng/Nhân viên/Sổ quỹ/Báo cáo/Bán online" hiện chỉ là placeholder disabled, `title="Sắp ra mắt"`)
  - [x] **Tab "Tổng quan" — ✅ XONG (02/09/2026)**, dữ liệu thật kiểu KiotViet: 4 stat card (doanh thu hôm nay, đơn hàng hôm nay, trả hàng tổng, giá trị đơn TB hôm nay — không có số so sánh kỳ trước vì backend chưa có, không bịa) + biểu đồ doanh thu (Chart.js, đổi được kỳ hạn hôm nay/7 ngày/30 ngày/90 ngày/năm) + "Top 10 hàng bán chạy" + "Sản phẩm sắp hết hàng" (thay cho "Top khách mua nhiều nhất" của KiotViet — không có endpoint khách hàng, dùng luôn `low-stock` đã có, đặt tên đúng bản chất) + "Hoạt động gần đây". Toàn bộ dùng data thật từ `AdminDashboardController` có sẵn từ Phase 2.3 (`overview`, `sales`, `top-products`, `order-status-stats`, `recent-activities`, `low-stock`) — không endpoint mới nào ở backend, chỉ sửa 1 bug nhỏ (thiếu space trong `"Đơn đặt hàng mới" + orderNumber`)
  - [x] **Tab "Hàng hóa" (quản lý sản phẩm) — ✅ XONG (02/09/2026)**, dùng trang hướng dẫn "Thêm mới hàng hóa" của KiotViet làm tham khảo cấu trúc (bảng danh sách + nút "Tạo mới" + form) — bỏ qua phần đa đơn vị quy đổi/biến thể vì `Product` entity không có khái niệm này. `ProductList` (bảng, tìm kiếm + lọc trạng thái — 2 cái này **không** kết hợp được ở backend nên UI vô hiệu hoá lọc trạng thái khi đang tìm kiếm thay vì giả vờ áp dụng cả hai, đổi trạng thái/xoá theo dòng, xoá chỉ OWNER) + `ProductForm` dùng chung cho tạo/sửa (route riêng `/dashboard/products/new` và `/dashboard/products/:id/edit`) + `ProductImageGallery` (chỉ hiện khi sửa — ảnh cần sản phẩm đã tồn tại; không có nút sắp xếp lại/đặt ảnh chính vì backend không có endpoint nào cho việc đó). Slug/SKU tự sinh từ tên (client-side, `slugify.ts`) vì backend không tự sinh và không kiểm tra trùng trước — trùng thì 500, form tự tạo lại mã mới và cho thử lại. Categories luôn đồng bộ qua endpoint `PATCH .../categories` riêng, **không** gửi trong body tạo/sửa (route PUT nhận field `categories` nhưng nó là raw JPA entity, gửi `{id}` rời rạc vào đó là hành vi không xác định — quyết định tránh hẳn). Các field backend âm thầm bỏ qua khi sửa (`slug`,`sku`,`shortDescription`,`featured`, mấy field meta) hiển thị dạng disabled kèm ghi chú thay vì ẩn đi, để chủ shop không thắc mắc sao sửa không lưu. Bỏ khỏi phạm vi lần này: cập nhật giá hàng loạt, tạo/sửa danh mục (chỉ đọc danh mục có sẵn), 3 field SEO (`metaTitle`/`metaDescription`/`metaKeywords`).
  - **Bổ sung Giá vốn + định mức tồn kho (02/09/2026)**: sau khi xem video hướng dẫn tạo hàng hóa thật của KiotViet (nhiều field hơn hẳn bản đầu: mã vạch, nhóm hàng/thương hiệu dropdown, giá vốn, định mức tồn, tích điểm, vị trí/kích thước, đa đơn vị quy đổi + biến thể thuộc tính) — chốt phạm vi chỉ thêm **Giá vốn** (`costPrice`) và **Định mức tồn thấp/cao nhất** (`minStockThreshold`/`maxStockThreshold`) làm field thật trên `Product`; cố tình bỏ qua phần restyle modal và toàn bộ hệ thống đa đơn vị/biến thể (không hợp với shop thời trang, KiotViet thiết kế cho F&B/bán lẻ tổng hợp). Backend: `V8` migration + 3 cột mới trên `Product` (`costPrice` có `@DecimalMin`, 2 ngưỡng tồn có `@Min`, đều optional không default) + `AdminProductController.updateProduct` copy 3 field mới khi có gửi lên. **Phát hiện phụ khi rà lại**: `costPrice` là giá vốn nội bộ, không được lộ ra ngoài — soát toàn bộ endpoint đọc sản phẩm công khai/không cần đăng nhập và ẩn field này trước khi trả về: 2 endpoint ở `StorefrontController` (`getStoreProducts`/`getStoreProduct`) + toàn bộ 10 endpoint GET ở `ProductController` (route `/products/**` cũ, public, không tenant-scope) qua helper `hideCostPrice()`. Frontend: `ProductDTO`/`CreateProductRequest`/`UpdateProductRequest` thêm 3 field, `ProductForm` thêm 2 nhóm "Giá vốn, giá bán" (giá vốn/giá bán/giá so sánh) và "Tồn kho" (tồn kho/định mức thấp nhất/định mức cao nhất) đúng cách nhóm của KiotViet thay vì 1 hàng 3 cột như bản đầu. Test: 76/76 (Vitest, không cần test mới — mở rộng cơ học trên `ProductAdminService`/`ProductForm` đã có test) + backend 88/88 (trừ `BackendApplicationTests` cần Docker) + `ng build` sạch + kiểm tra bằng Chromium headless xác nhận 2 nhóm field mới hiện đúng trên `/dashboard/products/new`.
  - **Chuyển ProductForm thành modal (02/09/2026)**: sau khi xem ảnh modal "Tạo hàng hóa" thật của KiotViet (cửa sổ nổi trên danh sách, không phải trang riêng), chốt phạm vi: đổi container thành modal + tổ chức lại thành tab "Thông tin"/"Mô tả" + 2 section thu gọn được ("Giá vốn, giá bán", "Tồn kho") — **không** thêm field mới (mã vạch, tích điểm, vị trí, đa đơn vị vẫn ngoài phạm vi như đã chốt trước đó). Kỹ thuật: route `new`/`:id/edit` chuyển thành route con của route rỗng `''` (chính `ProductList`) thay vì con trực tiếp của `products` — `ProductList` giờ có `<router-outlet>` riêng để `ProductForm` (giờ tự vẽ overlay `fixed inset-0` + hộp trắng ở giữa) render đè lên trong khi danh sách vẫn đứng yên phía sau, không unmount/refetch lại. Vì `ProductForm` với `ProductList` giờ là 2 route riêng (không còn cùng 1 instance như hồi còn là trang riêng), thêm signal `changed`/`notifyChanged()` vào `ProductAdminService` (thay cho `refreshTick` cục bộ cũ trong `ProductList`) để `ProductForm` báo cho danh sách phía sau tự fetch lại sau khi lưu thành công. Nút "Bỏ qua"/dấu X điều hướng về `/dashboard/products` (đóng modal, không mất dữ liệu list). Test: 76/76 vẫn xanh (không đổi test, thay đổi thuần cấu trúc route + template) + `ng build` sạch + Chromium headless xác nhận: modal nổi đúng vị trí (list mờ phía sau), chuyển tab Thông tin ↔ Mô tả hoạt động, thu gọn/mở rộng section "Giá vốn, giá bán" hoạt động, "Bỏ qua" điều hướng đúng về danh sách.
  - **Thuế suất (VAT %), chỉ OWNER (02/09/2026)**: tham khảo trang hướng dẫn "Giá bán sau thuế" của KiotViet nhưng chốt phạm vi đơn giản hơn nhiều bản gốc (2 mức VAT nhập/bán + tự quy đổi giá trước↔sau thuế) — chỉ thêm 1 field phẳng **Thuế suất** (`taxRate`, %) trên `Product`, không tính lại giá tự động, `price` giữ nguyên nghĩa là giá khách trả. Yêu cầu rõ: chỉ chủ shop (OWNER) được thấy/sửa field này, MANAGER thì không. Backend: `V9` migration + field `taxRate` (`@DecimalMin(0)`/`@DecimalMax(100)`) + `AdminProductController` inject sẵn `AuthorizationService` (dùng lại, không tạo cơ chế mới) để chặn ở tầng field bên trong 2 endpoint dùng chung OWNER+MANAGER: `createProduct` xoá `taxRate` khỏi request nếu người gọi không phải OWNER trước khi save, `updateProduct` chỉ copy `taxRate` khi `hasRole("OWNER")` — MANAGER gửi field này lên coi như không có, không lỗi, không lộ. Vì đây cũng là dữ liệu nội bộ (giống `costPrice`), gộp luôn vào cùng cơ chế ẩn công khai đã có: đổi tên `hideCostPrice()` → `hideInternalFields()` ở cả `StorefrontController` và `ProductController` (route `/products/**` cũ), giờ ẩn cả 2 field trên mọi endpoint đọc công khai. Frontend: `ProductForm` inject `AuthService`, field "Thuế suất (VAT %)" chỉ render trong khối `@if (isOwner())` bên trong section "Giá vốn, giá bán" (ẩn hẳn với MANAGER, không phải dạng disabled-có-ghi-chú như các field khác — đây là ranh giới quyền chứ không phải giới hạn vòng đời record) + submit chỉ gửi `taxRate` khi `isOwner()` để phòng thủ 2 lớp. Test: 76/76 frontend + 88/88 backend (như cũ) + `ng build` sạch + Chromium headless xác nhận field hiện đúng với JWT giả OWNER, biến mất hoàn toàn với JWT giả MANAGER (cùng 1 form, không lỗi console).
  - Kiến trúc mới cho app: `features/dashboard/` có nhiều file lần đầu tiên (trước giờ mỗi feature 1 file) — `DashboardOverview` (điều phối 6 lời gọi API độc lập qua `toSignal`, không dùng `forkJoin` để mỗi phần tự hiển thị ngay khi xong) + `DashboardTabs`/`StatCard`/`RevenueChart`/`RankedBarList`/`RecentActivity` (component con đầu tiên trong app, dùng `input()`/`model()`/`viewChild()` signal-based). Thêm `chart.js` (dependency mới đầu tiên cho biểu đồ, không dùng wrapper `ng2-charts`). Route `/dashboard` giờ có `children` thật (trước chỉ nhúng thẳng `DashboardOverview`) — Tổng quan là child rỗng `''`, Hàng hóa là route con `products` (gom nhóm 3 route con `''`/`new`/`:id/edit` dưới 1 guard chung `ownerManagerGuard` mới thay vì lặp lại role-check 3 lần). `VndCurrencyPipe` dời từ `features/storefront/` sang `core/currency/` vì giờ 2 feature cùng dùng
  - Test: 76/76 (Vitest, thêm `DashboardStatsService`/`ProductAdminService`/`ProductImageService`/`ProductCategoryService`/`ownerManagerGuard`) + `ng build` sạch + kiểm tra thật bằng Chromium headless với JWT giả (vì các route này yêu cầu đăng nhập OWNER/MANAGER) — xác nhận toàn bộ widget mới (Chart.js, form tạo/sửa, upload ảnh multipart) chạy không lỗi dù mọi API 500 (không có backend trong sandbox), và `ownerManagerGuard` redirect đúng session STAFF khỏi `/dashboard/products`
  - **Bài học công cụ**: giữa chừng gặp lỗi "Cannot match any routes" rồi sau đó "Failed to fetch dynamically imported module" khi test qua Chromium — tưởng là bug thật, hoá ra do lệnh `lsof` không có sẵn trong shell nên các lần "tắt server cũ" trước đó đều âm thầm thất bại, dẫn tới 1 tiến trình `ng serve` sống sót xuyên suốt nhiều lần sửa file dồn dập và rối trạng thái build incremental của Vite. Xác nhận bằng `Get-NetTCPConnection` (PowerShell) thấy đúng 1 process từ rất sớm vẫn còn sống, kill hẳn rồi build lại sạch mới hết lỗi — dùng PowerShell để tắt server thay vì `lsof` từ giờ trong môi trường này.
- [x] **Storefront theo store** (`/store/[slug]`) + **giỏ hàng** + **checkout** — ✅ XONG (02/09/2026), route param đầu tiên trong app. `StorefrontHome` (banner + lưới sản phẩm phân trang, danh mục hiện dạng chip tĩnh — API `/stores/{slug}/products` chưa hỗ trợ lọc theo category) + `StorefrontProductDetail` (chọn size/màu/số lượng, thêm giỏ hàng — chưa đăng nhập thì chuyển `/login` kèm `returnUrl`) + `StorefrontCart` + `StorefrontCheckout` (2 bước: địa chỉ+mã giảm giá → chọn phương thức thanh toán) + `/payment/success`/`/payment/cancel`. `CartService` mới trong `core/cart/` (Signals, giống `AuthService`), badge giỏ hàng trên header. sessionStorage (`pending-order.storage.ts`) mang `orderId`/`storeSlug` qua vòng redirect PayPal/MoMo vì backend tự dựng `successUrl` không có query string.
  - **Bug phát hiện khi làm**: giỏ hàng backend trước đó là 1-giỏ/user bất kể cửa hàng (`Cart.findByUserId` bỏ qua store dù DB đã có `UNIQUE(user_id, store_id)` từ đầu) — khách mua ở 2 cửa hàng khác nhau bị gộp chung giỏ. Đã sửa: `Cart.user` `@OneToOne`→`@ManyToOne`, `CartRepository.findByUserIdAndStoreId` mới, `CartController`/`OrderController.checkout`/3 chỗ xoá giỏ hàng trong `PaymentController` đều theo `(userId, storeId)`; `GET/DELETE /cart*` nhận thêm query param `storeSlug`, `POST /orders/checkout` nhận thêm field `storeSlug`. Tiện thể sửa luôn 1 typo cũ `"hasPay payment"` → `"hasPayment"` trong response `/payments/order/{id}`.
  - Test: 50/50 (Vitest, thêm `CartService`/`StorefrontCatalogService`/`StorefrontPaymentService`) + `ng build` sạch + kiểm tra thật bằng Playwright headless (không có backend chạy được trong sandbox — dùng để xác nhận app không crash runtime, error state hiển thị đúng, route guard hoạt động đúng khi build/test không phát hiện được). Backend: 88/88 test cũ vẫn xanh.
  - Lưu ý: subdomain wildcard (`shop1.domain.com`) cần custom domain trên Vercel; bản demo dùng path-based `/store/[slug]` là đủ, ghi rõ trong README hướng nâng cấp subdomain. Chưa làm: "đơn hàng của tôi" (lịch sử đơn cho khách), lọc sản phẩm theo category/tìm kiếm trong storefront
  - **Gap phát hiện sau khi deploy**: route `/store/:slug` mới không có link nào từ giao diện hiện có trỏ tới — chủ shop không có cách nào tự tìm ra URL storefront của mình (JWT chỉ có `storeId` số, không có `slug`). Thêm `GET /store` (backend, OWNER/MANAGER, `StoreProfileController` mới — trả về store hiện tại qua `TenantContext`, cùng shape với `StorefrontController`) + `StoreProfileService` (frontend, `core/store/`) + nút "Xem storefront →" trên `/dashboard` khi có `storeSlug`
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
- [x] **BUG (01/09/2026, đã fix): Neon báo "exceeded compute time quota"** — nguyên nhân: UptimeRobot ping `/api/actuator/health` (có check DB) mỗi 5 phút suốt 24/7, mà Neon free tier autosuspend sau 5 phút không hoạt động → ping liên tục giữ compute gần như luôn "active", đốt hết quota **100 compute-hours/tháng** (đã dùng hết từ 17/08, xác nhận qua email "Neon Alerts") sớm hơn nhiều so với lượng traffic demo thật cần.
  - [x] Code: thêm `management.endpoint.health.probes.enabled=true` vào `application.properties` → có sẵn `/actuator/health/liveness` (không đụng DB) song song với `/actuator/health` (đầy đủ, có DB)
  - [x] Đổi target UptimeRobot: `/api/actuator/health` → `/api/actuator/health/liveness` (monitor mới, region cùng lúc reset stats)
  - [x] Project cũ (`late-frost-75119354`, org `org-damp-silence-62000617`) vẫn bị khoá, không rõ ngày reset chính xác (Billing page không hiện, email chỉ ghi "resets next month") → **tạo project Neon mới** `ecommerce` (id `quiet-art-30117500`, region AWS Asia Pacific 1 Singapore, PG18) để chạy ngay không cần đợi/trả phí; đổi `DB_HOST/DB_USERNAME/DB_PASSWORD/DB_NAME` trên Render sang project mới (dùng host **không** có hậu tố `-pooler` vì Flyway advisory lock không hợp với pgbouncer transaction pooling); Flyway tự chạy `V1`→`V5` trên DB trống, `StoreSampleDataSeeder` tự seed lại data mẫu. Data demo cũ (nếu có) mất, chấp nhận được vì chỉ là project portfolio.
  - Bài học: bất kỳ health-check/uptime-ping định kỳ nào cũng phải trỏ endpoint **không đụng DB** — chỉ traffic thật mới nên đánh thức Neon.
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
