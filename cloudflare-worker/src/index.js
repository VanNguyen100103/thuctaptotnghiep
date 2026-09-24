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

    // Sheets first, Telegram second, on purpose. Appending a row is the more
    // fragile of the two - an expired token, a sheet nobody shared, a quota -
    // and putting it first means a failure returns 502 before anything has
    // been sent, so the outbox's retry is clean. The other order would post
    // the same alert to Telegram once per attempt.
    const logged = await appendToSheet(env, event);
    if (!logged.ok) {
      return new Response(`Sheets rejected delivery ${delivery}: ${logged.detail}`, { status: 502 });
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

/* ------------------------------------------------------------------ *
 * Google Sheets
 *
 * Optional: with no GOOGLE_* configured this does nothing and reports
 * success, so the Telegram half works on its own. Configured and broken is a
 * different matter and is reported, because a shop that thinks its takings
 * are being logged and finds an empty sheet at the end of the month is worse
 * off than one that was told.
 * ------------------------------------------------------------------ */

const SHEETS_SCOPE = 'https://www.googleapis.com/auth/spreadsheets';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';

/**
 * Access tokens last an hour and cost a round trip each. A Worker isolate
 * survives between requests, so one is reused until shortly before it
 * expires - "shortly" because the alternative is discovering it went stale
 * mid-append.
 */
let cachedToken = null;

/** One row per completed sale. Other events are not accounting records and are skipped. */
function sheetRow(event) {
  if (event.event !== 'sale.completed') {
    return null;
  }
  const d = event.data || {};
  return [
    event.occurredAt || '',
    d.code || '',
    d.customerName || 'Khách lẻ',
    d.customerPhone || '',
    d.itemCount ?? '',
    // Plain number, not the formatted string Telegram gets: a spreadsheet has
    // to be able to sum this column.
    Number(d.totalAmount || 0),
    d.cashier || '',
    // The outbox keeps this stable across retries, so a duplicated row is
    // recognisable rather than merely suspicious.
    event.eventId || '',
  ];
}

async function appendToSheet(env, event) {
  const row = sheetRow(event);
  if (!row) {
    return { ok: true };
  }
  if (!env.GOOGLE_SHEET_ID || !env.GOOGLE_SERVICE_ACCOUNT_EMAIL || !env.GOOGLE_PRIVATE_KEY) {
    return { ok: true };
  }

  let token;
  try {
    token = await accessToken(env);
  } catch (e) {
    return { ok: false, detail: 'token: ' + e.message };
  }

  const range = env.GOOGLE_SHEET_RANGE || 'A:H';
  const url =
    `https://sheets.googleapis.com/v4/spreadsheets/${env.GOOGLE_SHEET_ID}` +
    `/values/${encodeURIComponent(range)}:append` +
    '?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS';

  const response = await fetch(url, {
    method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify({ values: [row] }),
  });
  if (response.ok) {
    return { ok: true };
  }
  // Sheets explains itself in JSON, unlike an Apps Script relay which would
  // hand back a page of HTML.
  const body = await response.text();
  let detail = body.slice(0, 200);
  try {
    detail = JSON.parse(body).error?.message || detail;
  } catch {
    // keep the raw slice
  }
  return { ok: false, detail: `HTTP ${response.status} ${detail}` };
}

async function accessToken(env) {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt > now + 60) {
    return cachedToken.value;
  }

  const claim = {
    iss: env.GOOGLE_SERVICE_ACCOUNT_EMAIL,
    scope: SHEETS_SCOPE,
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600,
  };
  const unsigned =
    base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' })) + '.' + base64Url(JSON.stringify(claim));

  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToDer(env.GOOGLE_PRIVATE_KEY),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));
  const jwt = unsigned + '.' + base64Url(new Uint8Array(signature));

  const response = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt,
    }),
  });
  const body = await response.json();
  if (!response.ok) {
    throw new Error(body.error_description || body.error || `HTTP ${response.status}`);
  }
  cachedToken = { value: body.access_token, expiresAt: now + (body.expires_in || 3600) };
  return cachedToken.value;
}

/**
 * The PEM out of a service-account JSON file, as a DER buffer.
 *
 * Accepts the key exactly as it appears inside that file - one line with
 * literal backslash-n between the base64 rows - as well as a real multi-line
 * paste. Pasting a multi-line secret into a dashboard is the step people get
 * wrong, so this removes the need to.
 */
function pemToDer(pem) {
  const base64 = pem
    .replace(/\\n/g, '\n')
    .replace(/-----BEGIN [^-]+-----/, '')
    .replace(/-----END [^-]+-----/, '')
    .replace(/\s+/g, '');
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

function base64Url(input) {
  const binary =
    typeof input === 'string'
      ? String.fromCharCode(...new TextEncoder().encode(input))
      : String.fromCharCode(...input);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
