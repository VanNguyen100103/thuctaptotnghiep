/**
 * Fires one signed event at the published workflow, the way the backend would.
 *
 *   node n8n/send-test-event.mjs https://tryum-n8n.onrender.com/webhook/tryum-events
 *
 * Worth having because it needs nothing else to be alive: no backend, no
 * database, no POS. If Telegram buzzes, the whole right-hand half of the
 * pipeline is proven - signature, Code node, Telegram credential, chat id -
 * and anything that fails afterwards is on the backend's side of the wire.
 * Without it, a silent Telegram could be any of six things.
 *
 * The secret is read from backend/.env.render.local rather than typed on the
 * command line, where it would sit in shell history forever.
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const url = process.argv[2];
if (!url) {
  console.error('Usage: node n8n/send-test-event.mjs <production-webhook-url>');
  process.exit(1);
}
if (url.includes('/webhook-test/')) {
  console.error('That is the Test URL. Use the Production URL (/webhook/), which only answers once the workflow is published.');
  process.exit(1);
}

const here = dirname(fileURLToPath(import.meta.url));
const envFile = join(here, '..', 'backend', '.env.render.local');

let secret;
try {
  const line = readFileSync(envFile, 'utf8')
    .split(/\r?\n/)
    .find((l) => l.startsWith('AUTOMATION_WEBHOOK_SECRET='));
  secret = line && line.slice('AUTOMATION_WEBHOOK_SECRET='.length).trim();
} catch {
  console.error(`Could not read ${envFile}`);
  process.exit(1);
}
if (!secret) {
  console.error(`AUTOMATION_WEBHOOK_SECRET is missing from ${envFile}`);
  process.exit(1);
}

const body = JSON.stringify({
  eventId: crypto.randomUUID(),
  event: 'sale.completed',
  storeId: 1,
  occurredAt: new Date().toISOString().slice(0, 19),
  data: {
    saleId: 0,
    code: 'HD-TEST',
    totalAmount: 250000.0,
    amountReceived: 250000.0,
    itemCount: 2,
    customerName: 'Khach thu nghiem',
    customerPhone: null,
    cashier: 'send-test-event.mjs',
    pointsEarned: 25,
  },
});

const timestamp = String(Math.floor(Date.now() / 1000));
const key = await crypto.subtle.importKey(
  'raw',
  new TextEncoder().encode(secret),
  { name: 'HMAC', hash: 'SHA-256' },
  false,
  ['sign']
);
const signature =
  'sha256=' +
  [...new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(`${timestamp}.${body}`)))]
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');

console.log('POST ' + url);
const started = Date.now();
const response = await fetch(url, {
  method: 'POST',
  headers: {
    'content-type': 'application/json',
    'x-tryum-event': 'sale.completed',
    'x-tryum-store': '1',
    'x-tryum-delivery': crypto.randomUUID(),
    'x-tryum-timestamp': timestamp,
    'x-tryum-signature': signature,
  },
  body,
});
const seconds = ((Date.now() - started) / 1000).toFixed(1);
const text = await response.text();
console.log(`HTTP ${response.status} in ${seconds}s — ${text.slice(0, 300)}`);

if (response.status === 200) {
  console.log('\nn8n accepted it. Telegram should buzz within a second or two.');
  console.log('If it does not, open n8n -> Executions and read the failed run.');
} else if (response.status === 404) {
  console.log('\n404 means the workflow is not published, or the URL is wrong.');
} else if (response.status === 500) {
  console.log('\nThe workflow ran and threw. Open n8n -> Executions; a bad signature');
  console.log('means TRYUM_WEBHOOK_SECRET on Render differs from the one in .env.render.local.');
}
process.exit(response.status === 200 ? 0 : 1);
