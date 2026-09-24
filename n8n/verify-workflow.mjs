/**
 * Tests the exported workflow, without n8n.
 *
 *   node n8n/verify-workflow.mjs
 *
 * Half of the signature contract is Java with a unit test behind it; the
 * other half is JavaScript inside a JSON file, which nothing would otherwise
 * ever run until a shop noticed its alerts had stopped. This pulls the Code
 * node straight out of the exported workflow and runs it the way n8n does,
 * against the same HMAC vector AutomationSignatureServiceTest pins - so the
 * two halves cannot drift apart quietly.
 *
 * No dependencies: node builtins only.
 */
import { readFileSync } from 'node:fs';
import crypto from 'node:crypto';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// Resolved against this file, so it runs from the repo root or from n8n/.
const here = dirname(fileURLToPath(import.meta.url));
const wf = JSON.parse(readFileSync(join(here, 'workflows', 'tryum-events-to-telegram.json'), 'utf8'));
const code = wf.nodes.find((n) => n.name === 'Verify and format').parameters.jsCode;

const SECRET = 'test-secret';
const JAVA_VECTOR_TS = '1730000000';
const JAVA_VECTOR_BODY = '{"event":"sale.completed","storeId":42}';
const JAVA_VECTOR_SIG = 'sha256=6f62249b723acecf2ebe3e9489db7582286c770f6498a9fc45592e9c0ad3066c';

let pass = 0;
let fail = 0;
const check = (name, fn) => {
  try {
    fn();
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

// The node's code, run the way n8n runs it.
const node = new Function('$env', '$input', 'require', 'Buffer', code);
const sign = (ts, body) =>
  'sha256=' + crypto.createHmac('sha256', SECRET).update(ts + '.' + body).digest('hex');

const run = (body, opts = {}) => {
  const ts = opts.ts || String(Math.floor(Date.now() / 1000));
  const raw = opts.raw !== false;
  const headers = {
    'x-tryum-timestamp': ts,
    'x-tryum-signature': opts.sig || sign(ts, body),
    'x-tryum-delivery': '0f9c0000-0000-0000-0000-000000000001',
  };
  const item = raw
    ? { json: { headers }, binary: { data: { data: Buffer.from(body, 'utf8').toString('base64') } } }
    : { json: { headers, body: JSON.parse(body) } };

  return node(
    { TRYUM_WEBHOOK_SECRET: SECRET, TRYUM_TELEGRAM_CHAT_ID: '-100123' },
    { all: () => [item] },
    (m) => {
      if (m !== 'crypto') throw new Error('builtin not allowed: ' + m);
      return crypto;
    },
    Buffer
  );
};

const sale = JSON.stringify({
  eventId: '0f9c0000-0000-0000-0000-000000000001',
  event: 'sale.completed',
  storeId: 42,
  occurredAt: '2026-09-24T21:15:30',
  data: {
    saleId: 7, code: 'HD000012', totalAmount: 200000.0, amountReceived: 200000.0,
    itemCount: 3, customerName: 'Nguyen Van A', cashier: 'thungan01', pointsEarned: 20,
  },
});

console.log('');
console.log('Java <-> JavaScript signature contract');

check('JS reproduces the vector pinned in AutomationSignatureServiceTest', () => {
  const got = sign(JAVA_VECTOR_TS, JAVA_VECTOR_BODY);
  assert(got === JAVA_VECTOR_SIG, 'got ' + got);
});

console.log('');
console.log('The exported Code node');

check('accepts a correctly signed sale.completed and formats it', () => {
  const out = run(sale);
  assert(out.length === 1, 'expected 1 item, got ' + out.length);
  assert(out[0].json.message.includes('HD000012'), 'no invoice code in message');
  assert(out[0].json.message.includes('200.000d'), 'money not formatted: ' + out[0].json.message);
  assert(out[0].json.chatId === '-100123', 'chat id not carried through');
});

check('rejects a tampered body', () => {
  const ts = String(Math.floor(Date.now() / 1000));
  let threw = false;
  try {
    run(sale.replace('200000', '20'), { ts, sig: sign(ts, sale) });
  } catch {
    threw = true;
  }
  assert(threw, 'a tampered body was accepted');
});

check('rejects a replayed (stale) request', () => {
  let threw = false;
  try {
    run(sale, { ts: String(Math.floor(Date.now() / 1000) - 3600) });
  } catch {
    threw = true;
  }
  assert(threw, 'an hour-old request was accepted');
});

check('ignores an event key it has no branch for', () => {
  const unknown = JSON.stringify({ eventId: 'x', event: 'tax.period_due', storeId: 42, data: {} });
  assert(run(unknown).length === 0, 'unknown event produced output');
});

check('formats order.created and inventory.low_stock too', () => {
  const order = JSON.stringify({
    eventId: 'a', event: 'order.created', storeId: 42,
    data: { orderNumber: 'DH000003', total: 450000, itemCount: 2, customerName: 'B', customerPhone: '0900' },
  });
  const low = JSON.stringify({
    eventId: 'b', event: 'inventory.low_stock', storeId: 42,
    data: { name: 'Ca phe sua', sku: 'CF-01', stockQuantity: 3, minStockThreshold: 5, triggeredBy: 'HD000012' },
  });
  assert(run(order)[0].json.message.includes('DH000003'), 'order message wrong');
  assert(run(low)[0].json.message.includes('CF-01'), 'low stock message wrong');
});

check('falls back to the parsed body when Raw Body is off', () => {
  // Same bytes on both sides here, so this path verifies. It is the decimal
  // case (200000.00 -> 200000) it cannot survive, which is why rawBody is on.
  const simple = JSON.stringify({
    eventId: 'c', event: 'order.created', storeId: 42,
    data: { orderNumber: 'DH1', total: 1000, itemCount: 1, customerName: 'C' },
  });
  assert(run(simple, { raw: false }).length === 1, 'parsed-body fallback failed');
});

console.log('');
console.log(pass + ' passed, ' + fail + ' failed');
process.exit(fail ? 1 : 0);
