# Đưa automation lên production — hướng dẫn từng bước

Làm theo đúng thứ tự từ trên xuống. Tổng cộng khoảng 30 phút.

**Trước khi bắt đầu, mở sẵn file này ra cạnh trình duyệt:**

```
backend/.env.render.local
```

Trong đó có 2 key đã sinh sẵn và danh sách biến cần dán. File này đã được
gitignore — không bao giờ commit nó.

> **Vì sao phải đúng thứ tự?** Bước cuối cùng (5) là bật công tắc ở backend.
> Bật nó trước khi n8n sẵn sàng thì mỗi đơn hàng sẽ ghi một dòng vào outbox,
> thử lại 6 lần trong 8 tiếng rồi chết. Không mất dữ liệu bán hàng, nhưng bạn
> sẽ phải dọn tay. Để nó ở cuối là xong chuyện.

---

## Bước 1 — Tạm dừng 2 monitor UptimeRobot (2 phút)

**Vì sao:** Render cho cả tài khoản 750 giờ chạy mỗi tháng. Backend đang bị
ping mỗi 5 phút nên không bao giờ ngủ, một mình nó ăn 720–744 giờ. Không còn
chỗ cho n8n. Dừng ping thì backend ngủ khi rảnh, hai service dùng chung thoải
mái.

1. Vào https://dashboard.uptimerobot.com/monitors
2. Tìm dòng `thuctaptotnghiep-backend.onrender.com/api/actuator/health`
3. Bấm dấu `...` ở cuối dòng → chọn **Pause**
4. Làm y như vậy với dòng `.../api/actuator/health/liveness`
5. **Giữ nguyên** dòng `thuctaptotnghiep-frontend.vercel.app` — Vercel không
   ăn giờ của Render

✅ **Đúng khi:** ô "Current status" hiện `1 Up, 2 Paused`.

---

## Bước 2 — Tạo bot Telegram (5 phút)

Làm bước này **trước** khi tạo service, vì lát nữa cần dán cả token lẫn chat
id vào ngay lúc tạo.

### 2a. Tạo bot, lấy token

1. Mở Telegram, tìm **@BotFather** (có tick xanh)
2. Gõ `/newbot`
3. Nó hỏi tên hiển thị → gõ gì cũng được, ví dụ `Tryum Alerts`
4. Nó hỏi username → **bắt buộc kết thúc bằng `bot`**, ví dụ `tryum_alerts_bot`
5. Nó trả về một dòng dạng:

```
Use this token to access the HTTP API:
8123456789:AAH_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

Chép cả chuỗi đó, kể cả phần số trước dấu `:`. Đây là **bot token**.

### 2b. Lấy chat id

1. Trong Telegram, bấm vào tên bot vừa tạo → **Start** (hoặc gõ `/start`)
2. Nhắn cho nó một tin bất kỳ, ví dụ `hello`
3. Mở trình duyệt, vào địa chỉ này (thay `<TOKEN>` bằng token ở trên):

```
https://api.telegram.org/bot<TOKEN>/getUpdates
```

4. Trong đống JSON trả về, tìm đoạn:

```json
"chat":{"id":123456789,"first_name":"..."}
```

Số `123456789` đó là **chat id**. Chép lại.

> Nếu trang trả về `{"ok":true,"result":[]}` rỗng thì bạn chưa nhắn cho bot.
> Quay lại, nhắn một tin, rồi load lại trang.

---

## Bước 3 — Tạo service n8n trên Render (10 phút)

### 3a. Tạo database RIÊNG cho n8n (10 phút)

> ⚠️ **Đừng trỏ n8n vào Neon của ứng dụng.** Đây là bài học phải trả giá
> ngày 2026-09-20: Neon tính tiền theo **giờ database được đánh thức**, free
> tier chỉ có ~400 giờ/tháng. n8n giữ một pool kết nối thường trực, nên nó
> sẽ khiến Neon không bao giờ được ngủ và đốt sạch quota trong khoảng hai
> tuần — kéo theo cả backend chết vì mất database.
>
> n8n phải có database của riêng nó, ở một nhà cung cấp **không đo theo giờ**.

**Aiven** là lựa chọn hợp: không đo giờ compute, không tự pause vì rảnh,
không cần thẻ, miễn phí vĩnh viễn.

1. Vào [console.aiven.io](https://console.aiven.io) → **Create service** →
   **PostgreSQL**
2. Chọn gói ghi **Free** — *không phải* gói nào đang tiêu trial credit, nếu
   không thì hết hạn dùng thử là service dừng
3. Region: chọn gần Singapore nhất trong danh sách gói free cho phép
4. Đợi service chuyển sang **Running** (vài phút)
5. Ở trang Overview, mục **Connection information**, chép ra notepad:

| Aiven hiển thị | Dán vào n8n với tên |
|---|---|
| `Host` | `DB_POSTGRESDB_HOST` |
| `Port` | `DB_POSTGRESDB_PORT` |
| `User` (thường là `avnadmin`) | `DB_POSTGRESDB_USER` |
| `Password` (bấm **Show**) | `DB_POSTGRESDB_PASSWORD` |
| `Database` (thường là `defaultdb`) | `DB_POSTGRESDB_DATABASE` |

File `backend/.env.render.n8n.local` đã điền sẵn `avnadmin`, `defaultdb` và
`DB_POSTGRESDB_SCHEMA=public` — vì đây là database của riêng n8n, không phải
chia schema với ai, nên nó dùng `public` được thoải mái.

Tiện thể vào tab **Settings** của service backend trên Render, ghi lại
**Region** (ví dụ `Singapore`) — lát nữa service n8n phải chọn đúng region đó.

### 3b. Tạo service

1. Góc trên phải Render → **`+ New`** → **Web Service**
2. Chọn nguồn **Existing Image**, KHÔNG phải Git repository
3. Ô Image URL, dán chính xác:

```
docker.io/n8nio/n8n:1.123.81
```

> **Vì sao 1.x chứ không phải 2.x.** Bản 2.x bắt buộc chạy Code node trong
> một tiến trình Node riêng ("task runner") và không cho tắt. Hai tiến trình
> Node không vừa 512 MB của gói Free: nó chết bằng
> `FATAL ERROR: JavaScript heap out of memory` rồi lặp vô hạn (đã đo, 2026-09-24).
> Bản 1.x cho tắt bằng `N8N_RUNNERS_ENABLED=false`, còn một tiến trình, và
> chạy ổn trên cùng gói đó.

4. Bấm **Connect** / **Next**
5. Điền:
   - **Name**: `tryum-n8n`
   - **Region**: đúng region của backend
   - **Instance Type**: **Free**

### 3c. Dán biến môi trường

Kéo xuống mục **Environment Variables**. Cần 19 biến sau — tất cả đều nằm
trong `backend/.env.render.local` nhóm `[B]`:

```
PORT                                   5678
DB_TYPE                                postgresdb
DB_POSTGRESDB_HOST                     (chép ở bước 3a)
DB_POSTGRESDB_PORT                     (chép ở bước 3a)
DB_POSTGRESDB_DATABASE                 (chép ở bước 3a)
DB_POSTGRESDB_USER                     (chép ở bước 3a)
DB_POSTGRESDB_PASSWORD                 (chép ở bước 3a)
DB_POSTGRESDB_SCHEMA                   n8n
DB_POSTGRESDB_SSL_ENABLED              true
DB_POSTGRESDB_SSL_REJECT_UNAUTHORIZED  false
N8N_ENCRYPTION_KEY                     (có sẵn trong file, 64 ký tự)
N8N_WEBHOOK_URL                        https://tryum-n8n.onrender.com
NODE_FUNCTION_ALLOW_BUILTIN            crypto
TRYUM_WEBHOOK_SECRET                   (có sẵn trong file, 64 ký tự)
TRYUM_TELEGRAM_CHAT_ID                 (chat id ở bước 2b)
GENERIC_TIMEZONE                       Asia/Ho_Chi_Minh
TZ                                     Asia/Ho_Chi_Minh
EXECUTIONS_DATA_PRUNE                  true
EXECUTIONS_DATA_MAX_AGE                168
```

> `N8N_WEBHOOK_URL` phải khớp URL thật. Nếu tên `tryum-n8n` đã có người khác
> dùng, Render sẽ đổi tên và URL khác đi — xem URL thật ở đầu trang service
> sau khi tạo xong, rồi quay lại sửa biến này.

6. Bấm **Create Web Service**

Render kéo image về và khởi động. Lần đầu mất 2–5 phút.

✅ **Đúng khi:** tab **Logs** hiện dòng:

```
Editor is now accessible via: https://tryum-n8n.onrender.com
```

❌ **Nếu log báo lỗi database:** kiểm tra `DB_POSTGRESDB_SSL_ENABLED=true`
(Aiven bắt buộc SSL) và tên biến `DB_POSTGRESDB_USER` — là `_USER`, không phải
`_USERNAME`.
❌ **`Error: self-signed certificate in certificate chain`** — Aiven ký chứng
chỉ Postgres bằng CA riêng của họ, và `node-postgres` mặc định không tin CA
lạ. Đặt `DB_POSTGRESDB_SSL_REJECT_UNAUTHORIZED=false`; kết nối vẫn được mã
hoá, chỉ là không xác thực danh tính CA. Muốn làm chặt thì tải **CA
certificate** ở trang Overview của Aiven, dán nguyên nội dung vào biến
`DB_POSTGRESDB_SSL_CA` rồi trả biến trên về `true`.

Backend không gặp lỗi này vì nó nối bằng JDBC với `sslmode=require`, mà
`require` trong PostgreSQL JDBC nghĩa là mã hoá nhưng **không** xác thực CA.


---

## Bước 4 — Cài đặt bên trong n8n (10 phút)

### 4a. Tạo tài khoản chủ sở hữu

1. Mở URL service, ví dụ `https://tryum-n8n.onrender.com`
2. n8n hiện form **Set up owner account** → điền email, họ tên, mật khẩu

> Đây **không phải** đăng ký với n8n.io. Tài khoản này nằm trong database của
> chính bạn, không có mail xác thực nào gửi đi. Nhưng **nhớ mật khẩu** — mất
> thì phải xoá schema `n8n` làm lại từ đầu.

Nếu nó mời nhập license key hoặc dùng thử bản trả phí: bỏ qua, bấm **Skip**.

### 4b. Import workflow

1. Vào mục **Workflows** ở menu trái
2. Góc trên phải bấm **`...`** → **Import from File**
3. Chọn file trên máy bạn:

```
C:\Users\ASUS\thuctaptotnghiep\n8n\workflows\tryum-events-to-telegram.json
```

✅ **Đúng khi:** hiện ra 3 node nối thành hàng ngang:
`Tryum events` → `Verify and format` → `Send to Telegram`

### 4c. Gắn token Telegram

1. Bấm đúp vào node **Send to Telegram**
2. Ô **Credential to connect with** → **Create new credential**
3. Dán **bot token** (bước 2a) vào ô **Access Token**
4. Bấm **Save** → n8n tự kiểm tra, hiện dấu tick xanh
5. Đóng cửa sổ node

### 4d. Bật workflow

Bản 1.x: góc trên phải có công tắc **Inactive** → gạt thành **Active**.

(Bản 2.x đổi thành nút **Publish** — nếu sau này nâng cấp thì tìm nút đó.)

⚠️ **Bắt buộc.** Chưa bật thì n8n trả 404 kèm đúng câu
`The requested webhook "POST tryum-events" is not registered`, và backend sẽ
thử lại vô ích suốt 8 tiếng.

✅ **Đúng khi:** bấm đúp node `Tryum events` thấy **Production URL** là
`https://tryum-n8n.onrender.com/webhook/tryum-events` — không phải Test URL
(`/webhook-test/`, chỉ sống khi trình soạn đang mở), không phải `localhost`
(nghĩa là biến `N8N_WEBHOOK_URL` chưa ăn).

### 4e. Thử n8n trước khi động vào backend

```bash
node n8n/send-test-event.mjs https://tryum-n8n.onrender.com/webhook/tryum-events
```

Script tự ký một hoá đơn giả bằng secret trong `backend/.env.render.local` rồi
bắn thẳng vào workflow. Không cần backend, không cần database, không cần POS.

```
HTTP 200 in 1.2s — ...
n8n accepted it. Telegram should buzz within a second or two.
```

Telegram kêu là **nửa bên phải đường ống đã xong**: chữ ký, node Code, token
Telegram, chat id. Bước 5 sau đó chỉ còn nối nửa bên trái vào.

Lần đầu có thể mất ~50 giây vì service đang ngủ — bình thường.

| Kết quả | Nghĩa |
|---|---|
| `HTTP 404` | Chưa Publish, hoặc URL sai |
| `HTTP 500` | Workflow chạy và ném lỗi — mở tab **Executions** đọc. Hay gặp nhất: secret hai bên lệch |
| `HTTP 200` nhưng Telegram im | Token hoặc chat id sai. Tab **Executions**, mở lần chạy đỏ |

## Bước 5 — Bật công tắc ở backend (3 phút)

Bước cuối. Chỉ làm khi 4d đã xong.

1. Render → service **thuctaptotnghiep-backend** → **Environment**
2. Bấm **Add Environment Variable**, thêm 3 biến (nhóm `[A]` trong file):

```
AUTOMATION_ENABLED         true
AUTOMATION_WEBHOOK_URL     https://tryum-n8n.onrender.com/webhook/tryum-events
AUTOMATION_WEBHOOK_SECRET  (có sẵn trong file, 64 ký tự)
```

> `AUTOMATION_WEBHOOK_SECRET` phải **giống hệt** `TRYUM_WEBHOOK_SECRET` đã
> dán ở bước 3c. Hai bên dùng chung một chuỗi để ký và kiểm chữ ký. Lệch một
> ký tự là n8n từ chối mọi sự kiện.

> Đường dẫn là `/webhook/` — **không phải** `/webhook-test/`. Cái thứ hai chỉ
> sống khi đang mở trình soạn workflow.

3. Bấm **Save Changes** → Render tự deploy lại backend, mất 2–3 phút

---

## Bước 6 — Thử

1. Mở dashboard bản production
2. Vào màn **Bán hàng** (POS), bán một hoá đơn bất kỳ
3. Chờ **15 giây đến 1 phút** → Telegram hiện:

```
Hoa don HD000042
Khach: Khach le
Tong: 150.000d (2 mat hang)
Thu ngan: admin
```

> **Vì sao có thể tới 1 phút:** n8n ngủ sau 15 phút không ai gọi. Lần đánh
> thức đầu mất ~50 giây, vượt thời gian chờ 20 giây của backend → lần gửi đầu
> thất bại, 1 phút sau thử lại là lọt. Lần thứ hai trở đi, khi n8n còn thức,
> chỉ mất ~15 giây.

---

## Khi không có gì xảy ra

Mọi sự kiện đều để lại dấu vết trong bảng `automation_events`. Vào
**Neon Console → SQL Editor**, chạy:

```sql
SELECT event_key, status, attempts, last_error, next_attempt_at
FROM automation_events
ORDER BY id DESC
LIMIT 10;
```

| Thấy gì | Nghĩa là | Sửa sao |
|---|---|---|
| Không có dòng nào | Backend chưa nhận biến mới | Kiểm tra `AUTOMATION_ENABLED=true`, xem Render đã deploy lại xong chưa |
| `PENDING`, `attempts=0`, đứng yên | Backend thiếu URL hoặc secret | Kiểm tra đủ cả 3 biến ở bước 5 |
| `last_error` chứa `404` | Workflow chưa publish, hoặc URL sai | Làm lại 4d; kiểm tra `/webhook/` chứ không phải `/webhook-test/` |
| `last_error` chứa `timed out` | n8n đang ngủ dậy | Bình thường. Chờ 1 phút, nó tự thử lại |
| `DELIVERED` nhưng Telegram im | Lỗi nằm phía n8n | Vào n8n → tab **Executions**, mở lần chạy đỏ xem node nào hỏng |
| `DEAD` | Hết 6 lần thử (~8 tiếng) | Sửa nguyên nhân xong thì hồi sinh bằng câu lệnh dưới |

Hồi sinh các sự kiện đã chết:

```sql
UPDATE automation_events
SET status = 'PENDING', attempts = 0, next_attempt_at = now()
WHERE status = 'DEAD';
```

### Lỗi hay gặp trong tab Executions của n8n

| Thông báo | Nguyên nhân |
|---|---|
| `TRYUM_WEBHOOK_SECRET is not set` | Quên dán biến này ở bước 3c |
| `Bad signature, refusing delivery` | Secret hai bên lệch nhau |
| `Stale delivery ... Ns old` | Đồng hồ hai máy lệch quá 5 phút |
| `builtin not allowed: crypto` | Thiếu `NODE_FUNCTION_ALLOW_BUILTIN=crypto` |
| `Bad Request: chat not found` | `TRYUM_TELEGRAM_CHAT_ID` sai, hoặc chưa bấm Start với bot |

---

## Vài điều cần nhớ về sau

- **Đừng đổi `N8N_ENCRYPTION_KEY`.** Đổi nó là token Telegram đã lưu trong
  n8n thành rác, phải nhập lại từ đầu.
- **Đừng bật lại monitor UptimeRobot cho backend** nếu vẫn muốn n8n chạy —
  xem lại phép tính 750 giờ trong [README.md](README.md).
- **Đừng commit `backend/.env.render.local`.** Nó đã được gitignore, đừng chủ
  động thêm vào git.
- Muốn thêm kênh khác (Zalo, email, Google Sheets): thêm node vào workflow
  n8n, **không phải sửa backend**. Đó là toàn bộ lý do kiến trúc này tồn tại.
