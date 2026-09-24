/**
 * Runs the Worker locally, without Cloudflare and without npm install.
 *
 *   node cloudflare-worker/verify-worker.mjs
 *
 * Node 18+ has the same Request/Response/crypto.subtle the Workers runtime
 * exposes, so the module under test here is the exact file that gets
 * deployed - not a copy of its logic. The one thing stubbed is global fetch,
 * so the Telegram call is captured instead of sent.
 *
 * The HMAC vector is the one AutomationSignatureServiceTest pins on the Java
 * side. If either half of the signing scheme drifts, this goes red before a
 * shop notices its alerts stopped.
 */
import worker from './src/index.js';

const SECRET = 'test-secret';
const JAVA_VECTOR_TS = '1730000000';
const JAVA_VECTOR_BODY = '{"event":"sale.completed","storeId":42}';
const JAVA_VECTOR_SIG = 'sha256=6f62249b723acecf2ebe3e9489db7582286c770f6498a9fc45592e9c0ad3066c';

const ENV = {
  TRYUM_WEBHOOK_SECRET: SECRET,
  TELEGRAM_BOT_TOKEN: '123:FAKE',
  TELEGRAM_CHAT_ID: '7429814751',
};

let pass = 0;
let fail = 0;
const check = async (name, fn) => {
  try {
    await fn();
    console.log('  PASS  ' + name);
    pass++;
  } catch (e) {
    console.log('  FAIL  ' + name + '  -> ' + e.message);
    fail++;
  }
};
const assert = (condition, message) => {
  if (!condition) throw new Error(message);
};

async function sign(ts, body) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(SECRET),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const sig = await crypto.subtle.sign('HMAC', key, encoder.encode(`${ts}.${body}`));
  return 'sha256=' + [...new Uint8Array(sig)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Captures what the Worker tried to send to Telegram, and decides what Telegram "replies". */
let telegramCalls = [];
let telegramReply = { status: 200, body: '{"ok":true}' };
globalThis.fetch = async (url, init) => {
  telegramCalls.push({ url, body: JSON.parse(init.body) });
  return new Response(telegramReply.body, { status: telegramReply.status });
};

async function post(body, opts = {}) {
  telegramCalls = [];
  const ts = opts.ts || String(Math.floor(Date.now() / 1000));
  const headers = {
    'content-type': 'application/json',
    'x-tryum-event': 'sale.completed',
    'x-tryum-store': '42',
    'x-tryum-delivery': '0f9c0000-0000-0000-0000-000000000001',
    'x-tryum-timestamp': ts,
    'x-tryum-signature': opts.sig || (await sign(ts, body)),
  };
  return worker.fetch(new Request('https://worker.test/', { method: 'POST', headers, body }), ENV);
}

const sale = JSON.stringify({
  eventId: '0f9c0000-0000-0000-0000-000000000001',
  event: 'sale.completed',
  storeId: 42,
  occurredAt: '2026-09-24T21:15:30',
  data: {
    saleId: 7,
    code: 'HD000012',
    totalAmount: 200000.0,
    amountReceived: 200000.0,
    itemCount: 3,
    customerName: 'Nguyen Van A',
    cashier: 'thungan01',
    pointsEarned: 20,
  },
});

console.log('');
console.log('Java <-> Worker signature contract');

await check('Worker reproduces the vector pinned in AutomationSignatureServiceTest', async () => {
  const got = await sign(JAVA_VECTOR_TS, JAVA_VECTOR_BODY);
  assert(got === JAVA_VECTOR_SIG, 'got ' + got);
});

console.log('');
console.log('Worker behaviour');

await check('accepts a signed sale.completed and sends it to Telegram', async () => {
  const res = await post(sale);
  assert(res.status === 200, 'status ' + res.status);
  assert(telegramCalls.length === 1, 'expected 1 Telegram call, got ' + telegramCalls.length);
  const sent = telegramCalls[0].body;
  assert(sent.chat_id === '7429814751', 'wrong chat id');
  assert(sent.text.includes('HD000012'), 'no invoice code: ' + sent.text);
  assert(sent.text.includes('200.000đ'), 'money not formatted: ' + sent.text);
  assert(sent.parse_mode === 'HTML', 'parse_mode missing');
});

await check('rejects a tampered body with 401 and sends nothing', async () => {
  const ts = String(Math.floor(Date.now() / 1000));
  const res = await post(sale.replace('200000', '20'), { ts, sig: await sign(ts, sale) });
  assert(res.status === 401, 'status ' + res.status);
  assert(telegramCalls.length === 0, 'a tampered event reached Telegram');
});

await check('rejects a replayed request with 401', async () => {
  const res = await post(sale, { ts: String(Math.floor(Date.now() / 1000) - 3600) });
  assert(res.status === 401, 'status ' + res.status);
  assert(telegramCalls.length === 0, 'a stale event reached Telegram');
});

await check('rejects an unsigned request with 401', async () => {
  const res = await post(sale, { sig: 'sha256=' + '0'.repeat(64) });
  assert(res.status === 401, 'status ' + res.status);
});

await check('accepts and ignores an event key it has no branch for', async () => {
  const unknown = JSON.stringify({ eventId: 'x', event: 'tax.period_due', storeId: 42, data: {} });
  const res = await post(unknown);
  assert(res.status === 200, 'status ' + res.status);
  assert(telegramCalls.length === 0, 'an unhandled event was sent anyway');
});

await check('formats order.created and inventory.low_stock', async () => {
  const order = JSON.stringify({
    eventId: 'a',
    event: 'order.created',
    storeId: 42,
    data: { orderNumber: 'DH000003', total: 450000, itemCount: 2, customerName: 'B', customerPhone: '0900' },
  });
  await post(order);
  assert(telegramCalls[0].body.text.includes('DH000003'), 'order message wrong');

  const low = JSON.stringify({
    eventId: 'b',
    event: 'inventory.low_stock',
    storeId: 42,
    data: { name: 'Cà phê sữa', sku: 'CF-01', stockQuantity: 3, minStockThreshold: 5, triggeredBy: 'HD000012' },
  });
  await post(low);
  assert(telegramCalls[0].body.text.includes('CF-01'), 'low stock message wrong');
});

await check('returns 502 when Telegram refuses, so the outbox retries', async () => {
  telegramReply = { status: 400, body: '{"ok":false,"description":"chat not found"}' };
  const res = await post(sale);
  telegramReply = { status: 200, body: '{"ok":true}' };
  assert(res.status === 502, 'status ' + res.status);
  assert((await res.text()).includes('chat not found'), 'Telegram reason not passed through');
});

await check('rejects GET with 405', async () => {
  const res = await worker.fetch(new Request('https://worker.test/', { method: 'GET' }), ENV);
  assert(res.status === 405, 'status ' + res.status);
});

await check('refuses to run with no signing secret', async () => {
  const res = await worker.fetch(
    new Request('https://worker.test/', { method: 'POST', body: '{}' }),
    { ...ENV, TRYUM_WEBHOOK_SECRET: '' }
  );
  assert(res.status === 500, 'status ' + res.status);
});

console.log('');
console.log(pass + ' passed, ' + fail + ' failed');
process.exit(fail ? 1 : 0);
