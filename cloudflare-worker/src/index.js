/**
 * Tryum -> Telegram, as a Cloudflare Worker.
 *
 * This is the far end of the outbox described in V43: the backend writes an
 * event inside the transaction that caused it, a scheduled dispatcher posts
 * it here signed with HMAC-SHA256, and this turns it into the message a shop
 * owner reads on their phone.
 *
 * Why a Worker and not an automation platform. The requirement is a shop
 * owner's: the alert has to arrive now, at any hour, and cost nothing. Every
 * hosted option that could satisfy the last two failed the first, because
 * they are all containers and a container has to be running to answer.
 * n8n on Render's free tier spins down after fifteen idle minutes and needs
 * about nine minutes to come back on a 0.1 CPU instance; keeping it awake
 * costs 730 of the 750 instance-hours the whole workspace gets in a month,
 * which leaves nothing for the application itself. A Worker has no container
 * to keep warm: it answers in milliseconds, always, on a free tier of
 * 100,000 requests a day.
 *
 * The trade is that adding a channel means adding a case to format() and a
 * deploy, rather than dragging a node onto a canvas. For three event types
 * and one channel that is the cheaper side of the bargain.
 *
 * Nothing is stored here. The Worker cannot recognise a redelivered event,
 * so a retry after a Telegram failure can send the same alert twice. That is
 * the right way round for a notification: a duplicate is noise, a miss is a
 * shop that never learned it had an order.
 */

/** How far apart the two clocks may be before a delivery is treated as replayed. */
const TOLERANCE_SECONDS = 300;

export default {
  async fetch(request, env) {
    if (request.method !== 'POST') {
      return new Response('Method not allowed', { status: 405 });
    }
    if (!env.TRYUM_WEBHOOK_SECRET) {
      // Refusing beats accepting unsigned: the backend retries, and a
      // misconfigured Worker shows up as a backlog rather than as silence.
      return new Response('TRYUM_WEBHOOK_SECRET is not set', { status: 500 });
    }

    // The exact bytes the backend signed. Never JSON.parse before verifying:
    // re-serializing rewrites numbers (200000.00 becomes 200000) and nothing
    // would ever verify again.
    const raw = await request.text();
    const timestamp = request.headers.get('x-tryum-timestamp') || '';
    const signature = request.headers.get('x-tryum-signature') || '';
    const delivery = request.headers.get('x-tryum-delivery') || 'unknown';

    const expected = 'sha256=' + (await hmacSha256Hex(env.TRYUM_WEBHOOK_SECRET, `${timestamp}.${raw}`));
    if (!timingSafeEqual(signature, expected)) {
      return new Response('Bad signature', { status: 401 });
    }

    const age = Math.abs(Math.floor(Date.now() / 1000) - Number(timestamp));
    if (!Number.isFinite(age) || age > TOLERANCE_SECONDS) {
      return new Response('Stale delivery', { status: 401 });
    }

    let event;
    try {
      event = JSON.parse(raw);
    } catch {
      return new Response('Body is not JSON', { status: 400 });
    }

    const message = format(event);
    if (!message) {
      // An event this Worker has no opinion about. Accepting it keeps the
      // backend from retrying something nobody wants, which is how a new
      // event key ships without a matching deploy here.
      return new Response(`Ignored ${event.event}`, { status: 200 });
    }

    const sent = await sendTelegram(env, message);
    if (!sent.ok) {
      // A non-2xx sends the event back into the outbox's backoff, and the
      // reason lands in automation_events.last_error where it can be read.
      return new Response(`Telegram rejected delivery ${delivery}: ${sent.detail}`, { status: 502 });
    }
    return new Response('ok', { status: 200 });
  },
};

async function hmacSha256Hex(secret, data) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign('HMAC', key, encoder.encode(data));
  return [...new Uint8Array(signature)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Workers have no crypto.timingSafeEqual, so compare by hand rather than with
 * ===, which returns as soon as two bytes differ and leaks how much of a
 * guess was right.
 */
function timingSafeEqual(a, b) {
  if (a.length !== b.length) {
    return false;
  }
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

/** "200000.00" -> "200.000đ". Done by hand because Workers' Intl data is not worth depending on for this. */
export function money(value) {
  const rounded = Math.round(Number(value || 0));
  return String(rounded).replace(/\B(?=(\d{3})+(?!\d))/g, '.') + 'đ';
}

/**
 * One event to one message, or null for events this Worker does not handle.
 * Adding a channel means adding a case; the backend stays untouched.
 */
export function format(event) {
  const d = event.data || {};
  switch (event.event) {
    case 'sale.completed':
      return [
        `🧾 <b>Hóa đơn ${d.code}</b>`,
        `Khách: ${d.customerName || 'Khách lẻ'}`,
        `Tổng: <b>${money(d.totalAmount)}</b> (${d.itemCount} mặt hàng)`,
        `Thu ngân: ${d.cashier || '-'}`,
      ].join('\n');

    case 'order.created':
      return [
        `🛒 <b>Đơn mới ${d.orderNumber}</b>`,
        `Khách: ${d.customerName || '-'}${d.customerPhone ? ' · ' + d.customerPhone : ''}`,
        `Tổng: <b>${money(d.total)}</b> (${d.itemCount} mặt hàng)`,
        '<i>Chưa thanh toán</i>',
      ].join('\n');

    case 'inventory.low_stock':
      return [
        '⚠️ <b>Sắp hết hàng</b>',
        `${d.name}${d.sku ? ' (' + d.sku + ')' : ''}`,
        `Còn ${d.stockQuantity}, định mức ${d.minStockThreshold}`,
        `Vừa bán ở ${d.triggeredBy}`,
      ].join('\n');

    default:
      return null;
  }
}

async function sendTelegram(env, text) {
  if (!env.TELEGRAM_BOT_TOKEN || !env.TELEGRAM_CHAT_ID) {
    return { ok: false, detail: 'TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is not set' };
  }
  const response = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      chat_id: env.TELEGRAM_CHAT_ID,
      text,
      parse_mode: 'HTML',
      disable_web_page_preview: true,
    }),
  });
  if (response.ok) {
    return { ok: true };
  }
  // Telegram explains itself in the body ("chat not found", "bot was blocked"),
  // and that sentence is what makes the failure fixable.
  return { ok: false, detail: `HTTP ${response.status} ${await response.text()}` };
}
