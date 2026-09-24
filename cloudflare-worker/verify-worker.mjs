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
console.log('Google Sheets');

/**
 * A throwaway RSA key, exported the way a service-account JSON file carries
 * it: one line, with literal backslash-n between the base64 rows. That
 * encoding is the step people get wrong when pasting a secret into a
 * dashboard, so the real signing path runs against it here rather than
 * against a tidy multi-line PEM.
 */
const { privateKey } = await crypto.subtle.generateKey(
  { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
  true,
  ['sign', 'verify']
);
const pkcs8 = new Uint8Array(await crypto.subtle.exportKey('pkcs8', privateKey));
const pem =
  '-----BEGIN PRIVATE KEY-----\\n' +
  btoa(String.fromCharCode(...pkcs8)).match(/.{1,64}/g).join('\\n') +
  '\\n-----END PRIVATE KEY-----\\n';

/** The same key pasted as a real multi-line PEM, which must work too. */
const pemMultiline = pem.replace(/\\n/g, '\n');

const SHEETS_ENV = {
  ...ENV,
  GOOGLE_SHEET_ID: 'sheet-123',
  GOOGLE_SERVICE_ACCOUNT_EMAIL: 'tryum-sheets@ecom.iam.gserviceaccount.com',
  GOOGLE_PRIVATE_KEY: pem,
};

let calls = [];
let sheetsReply = { status: 200, body: '{"updates":{"updatedRows":1}}' };
globalThis.fetch = async (url, init) => {
  calls.push({ url: String(url), init });
  if (String(url).startsWith('https://oauth2.googleapis.com/')) {
    return new Response(JSON.stringify({ access_token: 'tok-abc', expires_in: 3600 }), { status: 200 });
  }
  if (String(url).startsWith('https://sheets.googleapis.com/')) {
    return new Response(sheetsReply.body, { status: sheetsReply.status });
  }
  telegramCalls.push({ url: String(url), body: JSON.parse(init.body) });
  return new Response(telegramReply.body, { status: telegramReply.status });
};

async function postWithSheets(body) {
  calls = [];
  telegramCalls = [];
  const ts = String(Math.floor(Date.now() / 1000));
  return worker.fetch(
    new Request('https://worker.test/', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-tryum-delivery': '0f9c0000-0000-0000-0000-000000000001',
        'x-tryum-timestamp': ts,
        'x-tryum-signature': await sign(ts, body),
      },
      body,
    }),
    SHEETS_ENV
  );
}

await check('signs a real JWT and appends one row per completed sale', async () => {
  const res = await postWithSheets(sale);
  assert(res.status === 200, 'status ' + res.status);

  const token = calls.find((c) => c.url.startsWith('https://oauth2.googleapis.com/'));
  assert(token, 'no token request');
  const assertion = new URLSearchParams(token.init.body).get('assertion');
  assert(assertion && assertion.split('.').length === 3, 'assertion is not a JWT: ' + assertion);
  const claim = JSON.parse(atob(assertion.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
  assert(claim.scope === 'https://www.googleapis.com/auth/spreadsheets', 'wrong scope: ' + claim.scope);
  assert(claim.iss === SHEETS_ENV.GOOGLE_SERVICE_ACCOUNT_EMAIL, 'wrong iss');

  const append = calls.find((c) => c.url.includes('/values/'));
  assert(append, 'no append request');
  assert(append.url.includes('sheet-123'), 'wrong spreadsheet');
  assert(append.init.headers.authorization === 'Bearer tok-abc', 'token not used');
  const row = JSON.parse(append.init.body).values[0];
  assert(row[1] === 'HD000012', 'invoice code column wrong: ' + row[1]);
  assert(row[5] === 200000, 'total must stay a number so the column can be summed: ' + row[5]);
  assert(row[7] === '0f9c0000-0000-0000-0000-000000000001', 'eventId column missing');
});

await check('accepts the key as a real multi-line PEM as well', async () => {
  const res = await worker.fetch(
    new Request('https://worker.test/', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-tryum-delivery': 'multiline',
        'x-tryum-timestamp': String(Math.floor(Date.now() / 1000)),
        'x-tryum-signature': await sign(String(Math.floor(Date.now() / 1000)), sale),
      },
      body: sale,
    }),
    { ...SHEETS_ENV, GOOGLE_PRIVATE_KEY: pemMultiline }
  );
  assert(res.status === 200, 'status ' + res.status);
});

await check('an event that is not a sale writes no row', async () => {
  const low = JSON.stringify({
    eventId: 'b',
    event: 'inventory.low_stock',
    storeId: 42,
    data: { name: 'Cà phê sữa', sku: 'CF-01', stockQuantity: 3, minStockThreshold: 5, triggeredBy: 'HD1' },
  });
  await postWithSheets(low);
  assert(!calls.some((c) => c.url.includes('/values/')), 'a low-stock event reached the spreadsheet');
  assert(telegramCalls.length === 1, 'but it should still reach Telegram');
});

await check('a Sheets failure returns 502 and sends no Telegram message', async () => {
  sheetsReply = { status: 403, body: '{"error":{"message":"The caller does not have permission"}}' };
  const res = await postWithSheets(sale);
  sheetsReply = { status: 200, body: '{"updates":{"updatedRows":1}}' };

  assert(res.status === 502, 'status ' + res.status);
  assert((await res.text()).includes('does not have permission'), 'Google reason not passed through');
  assert(telegramCalls.length === 0, 'Telegram was sent despite the row failing');
});

console.log('');
console.log(pass + ' passed, ' + fail + ' failed');
process.exit(fail ? 1 : 0);
