/**
 * Fires one signed event at the deployed Worker, the way the backend would.
 *
 *   node cloudflare-worker/send-test-event.mjs https://tryum-automation.xxx.workers.dev
 *
 * Useful because it needs nothing else to be alive: no backend, no database,
 * no POS. If Telegram buzzes, the whole right-hand half of the pipeline -
 * signature, formatting, bot token, chat id - is proven working, and anything
 * that fails afterwards is on the backend's side of the wire.
 *
 * The secret is read from backend/.env.render.local rather than typed on the
 * command line, where it would sit in shell history forever.
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const url = process.argv[2];
if (!url) {
  console.error('Usage: node cloudflare-worker/send-test-event.mjs <worker-url>');
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
    customerName: 'Khách thử nghiệm',
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
const seconds = ((Date.now() - started) / 1000).toFixed(2);
console.log(`HTTP ${response.status} in ${seconds}s — ${(await response.text()).slice(0, 200)}`);

if (response.status === 200) {
  console.log('\nTelegram should have buzzed. If it did not, check the bot token and chat id.');
} else if (response.status === 401) {
  console.log('\nThe Worker rejected the signature: its TRYUM_WEBHOOK_SECRET differs from the one in .env.render.local.');
} else if (response.status === 500) {
  console.log('\nThe Worker has no TRYUM_WEBHOOK_SECRET set. Run: npx wrangler secret put TRYUM_WEBHOOK_SECRET');
}
