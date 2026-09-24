# n8n — lớp tự động hóa của Tryum

Tryum không tự gọi Telegram, Zalo, Google Sheets hay Slack. Nó phát ra
**sự kiện**, ký rồi đẩy sang n8n, và n8n là chỗ duy nhất biết các API đó.
Nhờ vậy thêm một kênh thông báo mới không phải sửa backend.

```
SaleService.checkout()          ← giao dịch nghiệp vụ
      │  (cùng transaction)
      ▼
automation_events               ← outbox, bảng ở V43
      │  AutomationDispatcher, mỗi 15s
      ▼
POST /webhook/tryum-events      ← ký HMAC-SHA256
      │
      ▼
n8n workflow                    ← Telegram / Zalo / Sheets / email
```

## Ranh giới — thứ n8n **không** được làm

n8n chỉ nhận những việc **an toàn khi chạy lại và chấp nhận được khi mất**.
Cụ thể là: thông báo, báo cáo, đồng bộ ra hệ thống ngoài.

Không đặt vào đây: thanh toán và hoàn tiền, trừ/cộng tồn kho, email giao dịch
(OTP, xác thực tài khoản — đã có chuỗi Brevo → Gmail trong `backend/.../email/`,
và n8n đang ngủ thì OTP chết), phân quyền. Những thứ đó phải đúng ngay lần
đầu, còn ở đây một sự kiện có thể tới muộn vài phút hoặc tới hai lần.

## Chạy local

**1. Bật n8n** (nó dùng chung Postgres với backend, schema riêng `n8n`):

```bash
cd backend
docker compose --profile automation up -d n8n
```

Không phải tạo schema `n8n` bằng tay: n8n tự tạo lúc boot (đã thử với 2.40.5
trên volume Postgres có sẵn — 142 bảng của nó nằm gọn trong schema riêng,
Flyway vẫn chỉ đụng `public`). File `docker/postgres-init/` chỉ là lưới an
toàn cho trường hợp bản n8n sau này bỏ hành vi đó.

> Dùng chung Postgres chỉ đúng **ở local**, nơi database là một container
> không ai tính tiền. Trên production thì **không**: xem [DEPLOY-RENDER.md](DEPLOY-RENDER.md)
> bước 3a — n8n giữ kết nối thường trực, và với một database tính theo giờ
> thức (Neon) thì điều đó đốt sạch quota tháng rồi kéo cả backend chết theo.

**2. Sinh secret** và điền vào `backend/.env` (cùng một chuỗi cho cả hai bên —
backend ký bằng nó, workflow kiểm bằng nó):

```bash
openssl rand -hex 32
```

```properties
AUTOMATION_ENABLED=true
AUTOMATION_WEBHOOK_URL=http://localhost:5678/webhook/tryum-events
AUTOMATION_WEBHOOK_SECRET=<chuỗi vừa sinh>
TRYUM_TELEGRAM_CHAT_ID=<chat id của bạn>
```

> Chạy backend bằng `./mvnw spring-boot:run` (ngoài Docker) thì
> `AUTOMATION_WEBHOOK_URL` là `http://localhost:5678/...`. Chạy backend
> **trong** compose thì phải là `http://n8n:5678/...` — hai container nói
> chuyện với nhau bằng tên service, không qua `localhost`.

**3. Tạo tài khoản n8n** (chỉ lần đầu): mở http://localhost:5678 — n8n hiện
form *Set up owner account*. Đây **không phải** đăng ký với n8n.io: tài khoản
này nằm trong Postgres của chính bạn, email điền gì cũng được (không có mail
xác thực nào gửi đi), miễn nhớ mật khẩu. Nó sống sót qua `docker compose down`
và chỉ mất nếu xoá schema `n8n`.

> n8n đã **bỏ basic auth từ bản 1.0** — `N8N_BASIC_AUTH_ACTIVE/USER/PASSWORD`
> giờ bị lờ đi im lặng, nên compose ở đây không đặt chúng. Cũng không có cách
> nào tắt màn hình đăng nhập ở bản mới. Compose ghim `n8nio/n8n:2.40.5` —
> bản đã chạy thử với setup này, thay vì `latest` trôi theo thời gian.

**4. Import workflow**: menu ⋯ (góc trên phải) → *Import from File* →
[`workflows/tryum-events-to-telegram.json`](workflows/tryum-events-to-telegram.json).

**5. Gắn credential Telegram**: [@BotFather](https://t.me/BotFather) → `/newbot`
→ lấy token → dán vào node *Send to Telegram*. Chat id lấy bằng cách nhắn cho
bot rồi mở `https://api.telegram.org/bot<TOKEN>/getUpdates`.

**6. Bật workflow** (toggle *Active* góc trên phải). **Bắt buộc** — khi chưa
active, n8n trả 404 cho `/webhook/...` và mọi sự kiện sẽ nằm retry.

Xong: bán một hóa đơn ở POS, trong ~15 giây Telegram sẽ kêu.

## Có cần đăng ký tài khoản n8n không?

Không. Community Edition self-host là bản miễn phí đầy đủ: toàn bộ node, Code
node, Webhook node, không giới hạn số workflow hay số lần chạy. Cái duy nhất
phải "đăng ký" là owner account ở bước 3, và nó nằm trong DB của bạn.

Hai thứ tuỳ chọn, không cần cho dự án này:

- **License key Community (miễn phí)** — điền email ở
  [n8n.io/community-edition](https://n8n.io/community-edition/), key gửi về
  mail, dán vào *Settings → Usage and plan*. Mở thêm: Folders, Debug in
  editor, Custom execution data, và workflow history 24 giờ.
- **n8n Cloud** — [cloud.n8n.io](https://cloud.n8n.io), dùng thử 14 ngày rồi
  trả phí. Khỏi tự vận hành, nhưng với đồ án thì self-host vừa miễn phí vừa
  là thứ đáng kể hơn khi đưa vào CV.

## Kiểm tra khi không có gì xảy ra

```sql
SELECT event_key, status, attempts, last_error, next_attempt_at
FROM automation_events ORDER BY id DESC LIMIT 10;
```

| Thấy gì | Nghĩa là |
|---|---|
| Không có dòng nào | `AUTOMATION_ENABLED` vẫn là `false` — publisher không ghi gì cả |
| `PENDING`, `attempts=0`, không đổi | Dispatcher không chạy: thiếu URL hoặc thiếu secret (xem `AutomationDispatcher#isConfigured`) |
| `PENDING`, `last_error` có `404` | Workflow chưa *Active*, hoặc URL đang trỏ `/webhook-test/` |
| `PENDING`, `last_error` có `Connection refused` | Container n8n chưa chạy, hoặc backend trong Docker đang gọi `localhost` |
| `DELIVERED` nhưng Telegram im | Lỗi nằm phía n8n — xem tab *Executions* |
| `DEAD` | Hết 6 lần thử (~8 tiếng). Sửa xong thì `UPDATE ... SET status='PENDING', attempts=0, next_attempt_at=now()` |

## Hợp đồng chữ ký

Mỗi request mang:

```
Content-Type: application/json
X-Tryum-Event: sale.completed
X-Tryum-Store: 42
X-Tryum-Delivery: 0f9c…          ← UUID, giữ nguyên qua mọi lần retry
X-Tryum-Timestamp: 1758700000
X-Tryum-Signature: sha256=ab34…  ← HMAC-SHA256(secret, "{timestamp}.{raw body}")
```

Ba điều bắt buộc với workflow mới:

1. **Ký trên raw body, không ký trên JSON đã parse.** Backend gửi
   `200000.00`, `JSON.stringify` sẽ trả về `200000`, và không gì khớp nữa.
   Node Webhook phải bật *Raw Body*.
2. **Timestamp nằm trong phần được ký**, nên một request bắt được hôm nay
   không dùng lại được ngày mai. Workflow từ chối gì quá 5 phút tuổi.
3. **Khử trùng lặp theo `X-Tryum-Delivery`** nếu workflow làm việc gì không
   lặp lại được. Retry là bình thường ở đây: n8n nhận rồi mới chết trong lúc
   xử lý thì Tryum vẫn coi là chưa gửi và gửi lại.

Phía Java: `AutomationSignatureService`, có test ghim vector HMAC
(`AutomationSignatureServiceTest`) đúng để đổi cách ký một bên là test đỏ
ngay, chứ không phải là cửa hàng phát hiện ra vì thông báo ngừng tới.

Chạy `node n8n/verify-workflow.mjs` để kiểm tra chính file workflow đã export
— không cần n8n, không cần `npm install`. Nó lôi node *Verify and format* ra
khỏi JSON và chạy đúng cách n8n chạy, trên cùng vector HMAC mà test Java ghim.

## Sự kiện đang có

| Key | Bắn khi | Dữ liệu chính |
|---|---|---|
| `sale.completed` | POS chốt hóa đơn | `code`, `totalAmount`, `itemCount`, `customerName`, `cashier` |
| `order.created` | Khách đặt trên storefront (**chưa** thanh toán) | `orderNumber`, `total`, `customerEmail`, `customerPhone` |
| `inventory.low_stock` | Hàng vừa tụt xuống dưới "Định mức tồn ít nhất" | `sku`, `name`, `stockQuantity`, `minStockThreshold` |

Envelope chung:

```json
{
  "eventId": "0f9c…",
  "event": "sale.completed",
  "storeId": 42,
  "occurredAt": "2026-09-24T21:15:30",
  "data": { }
}
```

`inventory.low_stock` chỉ bắn đúng **lúc vượt ngưỡng**, không bắn mỗi lần bán
một sản phẩm đã ở dưới ngưỡng — nếu không, một mặt hàng bán chạy mà chưa kịp
nhập sẽ gửi một cảnh báo cho mỗi đơn vị bán ra.

## Thêm một sự kiện mới

1. Thêm hằng vào `AutomationEvents`.
2. Gọi `automationEventPublisher.publish(key, storeId, data)` **sau** khi dữ
   liệu đã lưu, và ở trong cùng transaction đó.
3. Thêm một `case` vào node *Verify and format*. Workflow cũ không vỡ: sự
   kiện lạ bị bỏ qua im lặng (`default: return null`).

Không cần thêm topic Kafka, không cần sửa `SecurityConfig`.

## Deploy thật

Hướng dẫn bấm-từng-nút để đưa lên Render có ở [DEPLOY-RENDER.md](DEPLOY-RENDER.md).
Phần dưới đây là những thứ cần hiểu, bất kể host ở đâu.

n8n cần state, nhưng state nằm ở Postgres (`DB_TYPE=postgresdb`) nên container
không cần ổ đĩa — chạy được trên free tier không có persistent disk.

- `N8N_ENCRYPTION_KEY`: đặt một lần và **giữ**. Đổi hoặc mất nó là mọi
  credential n8n đã lưu thành rác.
- `N8N_WEBHOOK_URL`: URL công khai của n8n. Không đặt thì n8n in ra URL
  localhost và bạn sẽ dán nhầm nó vào backend. (Tên cũ `WEBHOOK_URL` vẫn
  chạy nhưng cảnh báo deprecated mỗi lần boot từ bản 2.x.)
- Không còn basic auth để đổi: bảo vệ instance bằng mật khẩu owner account
  đủ mạnh, và đừng phơi cổng 5678 ra Internet nếu không cần.
- Free tier ngủ sau ~15 phút: request đầu tiên sau khi ngủ sẽ timeout. Đây
  chính là lý do tồn tại của outbox — lần retry sau một phút sẽ đi lọt.
- **750 instance-hours/tháng là của cả workspace Render, không phải mỗi
  service.** Một service chạy 24/7 ngốn ~730 giờ, tức gần hết quota một
  mình; hết giờ thì *mọi* free service treo tới đầu tháng sau. Service
  đang ngủ không tính giờ, nên hai service cùng ngủ khi rảnh thì vẫn đủ.
  Cái phá vỡ tính toán này là một uptime monitor ping backend mỗi 5
  phút: nó giữ backend thức 24/7 và không còn giờ cho n8n. Kiểm tra
  monitor trước khi tạo service thứ hai.

## Chưa làm (Phase 2–4)

Phase 1 là một workflow, một Telegram chat, cấu hình bằng biến môi trường.
Còn thiếu: bảng `store_automation` + tab "Tự động hóa" để chủ shop tự bật/tắt
recipe, màn lịch sử chạy, API key cho chiều n8n → Tryum (`/api/integration/**`),
và các recipe chạy theo lịch (chốt sổ cuối ngày, giỏ hàng bỏ quên, nhắc hạn
thuế 01/CNKD).
