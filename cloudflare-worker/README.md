# Lớp automation của Tryum — Cloudflare Worker

Backend không tự gọi Telegram. Nó ghi sự kiện vào outbox (`automation_events`,
V43) trong đúng transaction tạo ra sự kiện đó, rồi một job quét đẩy sang đây
bằng webhook có ký HMAC. Worker này kiểm chữ ký và gửi tin.

```
SaleService.checkout()        ← giao dịch nghiệp vụ
      │ (cùng transaction)
      ▼
automation_events             ← outbox, có retry/backoff
      │ AutomationDispatcher, mỗi 15s
      ▼
POST https://tryum-automation.<bạn>.workers.dev
      │ ký HMAC-SHA256 trên "{timestamp}.{raw body}"
      ▼
Cloudflare Worker  →  Telegram        (~10ms, 24/7)
```

## Vì sao là Worker

Yêu cầu là của người bán hàng: thông báo phải tới **ngay**, vào **bất kỳ giờ
nào**, và **không tốn tiền**.

Mọi nền tảng automation có sẵn đều là container, mà container phải đang chạy
mới trả lời được. Đã thử n8n trên Render free và đụng đúng bức tường đó:

| | Con số đo được |
|---|---|
| n8n ngủ sau | 15 phút không ai gọi |
| Thời gian boot lại (0.1 CPU) | **~9 phút**, trong lúc đó trả `503 Database is not ready!` |
| Giữ thức 24/7 tốn | **730 / 750** instance-hours của cả workspace Render |
| Còn lại cho backend | 20 giờ → không đủ |

Worker không có container nào để giữ ấm. Nó trả lời trong vài mili-giây, mọi
lúc, trên gói miễn phí **100.000 request/ngày**. Không ngủ, không cold start,
không quota giờ để canh.

Cái phải trả: thêm một kênh (Zalo, email, Sheets) là thêm một `case` trong
`format()` rồi deploy, không phải kéo node lên canvas. Với ba loại sự kiện và
một kênh thì đó là phía rẻ hơn của cuộc đổi chác.

## Deploy — 15 phút

### 1. Tài khoản Cloudflare

Đăng ký miễn phí ở [dash.cloudflare.com/sign-up](https://dash.cloudflare.com/sign-up).
Chỉ cần email, không cần thẻ, không cần tên miền.

### 2. Đăng nhập từ máy

```bash
cd cloudflare-worker
npx wrangler login
```

Nó mở trình duyệt để bấm **Allow**, xong quay lại terminal.

### 3. Deploy

```bash
npx wrangler deploy
```

Lần đầu nó hỏi đăng ký một subdomain `*.workers.dev` — gõ gì cũng được. Kết
thúc nó in ra:

```
Deployed tryum-automation
  https://tryum-automation.<subdomain>.workers.dev
```

**Chép URL đó lại.** Đây là `AUTOMATION_WEBHOOK_URL`.

### 4. Nạp 3 secret

Cloudflare mã hoá và giữ ở phía họ — không nằm trong repo, không nằm trong
`wrangler.toml`.

```bash
npx wrangler secret put TRYUM_WEBHOOK_SECRET
npx wrangler secret put TELEGRAM_BOT_TOKEN
npx wrangler secret put TELEGRAM_CHAT_ID
```

Mỗi lệnh hỏi giá trị, dán vào rồi Enter:

| Secret | Lấy ở đâu |
|---|---|
| `TRYUM_WEBHOOK_SECRET` | `AUTOMATION_WEBHOOK_SECRET` trong `backend/.env.render.local` — **phải giống hệt** |
| `TELEGRAM_BOT_TOKEN` | BotFather trả về sau `/newbot` hoặc `/revoke` |
| `TELEGRAM_CHAT_ID` | Số trong khối `"chat"` của `getUpdates` |

Secret có hiệu lực ngay, không cần deploy lại.

### 5. Bắn thử — chưa cần backend

```bash
node cloudflare-worker/send-test-event.mjs https://tryum-automation.<subdomain>.workers.dev
```

Script tự đọc secret từ `backend/.env.render.local`, ký một hoá đơn giả và gửi
đi.

```
HTTP 200 in 0.31s — ok

Telegram should have buzzed.
```

Telegram kêu là **toàn bộ nửa bên phải của đường ống đã chạy**: chữ ký, định
dạng tin, bot token, chat id. Không cần database, không cần backend sống.

### 6. Bật ở backend

Chỉ làm khi bước 5 đã xanh.

Render → service `thuctaptotnghiep-backend` → **Environment** → thêm:

```
AUTOMATION_ENABLED         true
AUTOMATION_WEBHOOK_URL     https://tryum-automation.<subdomain>.workers.dev
AUTOMATION_WEBHOOK_SECRET  (giống hệt TRYUM_WEBHOOK_SECRET ở bước 4)
```

**Save Changes** → Render deploy lại backend.

### 7. Thử thật

Bán một hoá đơn trên POS production. Trong khoảng **15 giây** Telegram hiện:

```
🧾 Hóa đơn HD000042
Khách: Khách lẻ
Tổng: 150.000đ (2 mặt hàng)
Thu ngân: admin
```

Mười lăm giây đó là chu kỳ quét của dispatcher, không phải độ trễ của Worker —
Worker trả lời trong vài mili-giây, kể cả 3 giờ sáng.

## Khi không có gì xảy ra

Mỗi sự kiện đều để lại dấu vết trong database:

```sql
SELECT event_key, status, attempts, last_error, next_attempt_at
FROM automation_events ORDER BY id DESC LIMIT 10;
```

| Thấy gì | Nghĩa là |
|---|---|
| Không có dòng nào | `AUTOMATION_ENABLED` chưa phải `true`, hoặc Render chưa deploy lại |
| `PENDING`, `attempts=0`, đứng yên | Backend thiếu URL hoặc thiếu secret |
| `last_error` chứa `401` | Secret hai bên lệch nhau |
| `last_error` chứa `502` | Worker nhận được nhưng Telegram từ chối — phần đuôi thông báo là lời giải thích của Telegram |
| `last_error` chứa `405` | URL trỏ sai chỗ |
| `DELIVERED` nhưng Telegram im | Gần như không xảy ra: Worker chỉ trả 200 sau khi Telegram nhận |
| `DEAD` | Hết 6 lần thử (~8 tiếng) |

Hồi sinh sự kiện đã chết:

```sql
UPDATE automation_events
SET status='PENDING', attempts=0, next_attempt_at=now()
WHERE status='DEAD';
```

Xem log phía Worker theo thời gian thực:

```bash
npx wrangler tail
```

Để cửa sổ đó mở rồi bán một hoá đơn — mỗi request hiện ra kèm mã trạng thái.

## Chạy test

```bash
node cloudflare-worker/verify-worker.mjs
```

Không cần `npm install`, không cần Cloudflare. Node nạp thẳng file sẽ được
deploy và gọi `fetch` của nó bằng `Request` thật, chỉ thay `globalThis.fetch`
để bắt lấy lời gọi Telegram.

Ba thứ nó khoá lại:

1. **Vector HMAC** — đúng chuỗi mà `AutomationSignatureServiceTest` ghim ở
   phía Java. Đổi cách ký một bên là test đỏ ngay, chứ không phải để cửa hàng
   phát hiện ra vì thông báo ngừng tới.
2. **Body sửa một chữ → 401, không gửi gì.** Chữ ký ký trên bytes gốc, nên
   Worker đọc `request.text()` trước khi `JSON.parse`. Parse rồi serialize lại
   sẽ biến `200000.00` thành `200000` và không gì khớp nữa.
3. **Telegram từ chối → 502**, để outbox thử lại thay vì nuốt lỗi.

## Thêm một sự kiện mới

1. Thêm hằng vào `AutomationEvents` (Java)
2. Gọi `automationEventPublisher.publish(key, storeId, data)` sau khi dữ liệu
   đã lưu, trong cùng transaction
3. Thêm một `case` vào `format()` trong [src/index.js](src/index.js)
4. `npx wrangler deploy`

Sự kiện chưa có `case` bị bỏ qua với HTTP 200 — Worker cũ không vỡ khi backend
bắt đầu phát ra key mới.

## Giới hạn đã biết

- **Không khử trùng lặp.** Worker không có bộ nhớ, nên nếu Telegram nhận rồi
  mà kết nối đứt trước khi Worker trả lời, outbox sẽ gửi lại và tin hiện hai
  lần. Đúng chiều đánh đổi cho một thông báo: trùng thì ồn, thiếu thì cửa hàng
  không biết mình có đơn.
- **Một chat cho tất cả cửa hàng.** `TELEGRAM_CHAT_ID` là secret của Worker,
  chưa phải cấu hình theo từng shop. Payload đã mang sẵn `storeId` nên khi nào
  làm tab "Tự động hóa" thì định tuyến theo nó.
