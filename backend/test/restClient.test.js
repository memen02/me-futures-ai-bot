'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { createRestClient } = require('../src/exchange/restClient');

function mockResponse({ ok = true, status = 200, body = {} }) {
  const text = typeof body === 'string' ? body : JSON.stringify(body);
  return { ok, status, text: async () => text };
}

const instantSleep = async () => {}; // supaya test retry tidak benar-benar menunggu

test('publicGet: membangun URL dengan query string yang benar dan parse JSON', async () => {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url, init });
    return mockResponse({ body: { serverTime: 123 } });
  };
  const client = createRestClient({ baseUrl: 'https://example.test', fetchImpl, sleepImpl: instantSleep });

  const result = await client.publicGet('/fapi/v1/time', { foo: 'bar' });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://example.test/fapi/v1/time?foo=bar');
  assert.equal(calls[0].init.method, 'GET');
  assert.deepEqual(result, { serverTime: 123 });
});

test('publicGet: tanpa params tidak menambahkan tanda "?"', async () => {
  const calls = [];
  const fetchImpl = async (url) => {
    calls.push(url);
    return mockResponse({ body: {} });
  };
  const client = createRestClient({ baseUrl: 'https://example.test', fetchImpl, sleepImpl: instantSleep });
  await client.publicGet('/fapi/v1/ping');
  assert.equal(calls[0], 'https://example.test/fapi/v1/ping');
});

test('publicGet: status 4XX non-transient (400) langsung melempar, TIDAK retry', async () => {
  let callCount = 0;
  const fetchImpl = async () => {
    callCount += 1;
    return mockResponse({ ok: false, status: 400, body: { code: -1100, msg: 'Bad request' } });
  };
  const client = createRestClient({ baseUrl: 'https://example.test', fetchImpl, sleepImpl: instantSleep });

  await assert.rejects(() => client.publicGet('/fapi/v1/time'), /Binance REST error 400/);
  assert.equal(callCount, 1, 'tidak boleh retry untuk error 400');
});

test('publicGet: status transient (500) di-retry lalu berhasil', async () => {
  let callCount = 0;
  const fetchImpl = async () => {
    callCount += 1;
    if (callCount < 3) return mockResponse({ ok: false, status: 500, body: 'Internal error' });
    return mockResponse({ body: { ok: true } });
  };
  const client = createRestClient({
    baseUrl: 'https://example.test',
    fetchImpl,
    sleepImpl: instantSleep,
    retryCount: 2,
  });

  const result = await client.publicGet('/fapi/v1/time');
  assert.equal(callCount, 3); // gagal, gagal, berhasil (percobaan ke-3)
  assert.deepEqual(result, { ok: true });
});

test('publicGet: tetap gagal setelah retryCount habis', async () => {
  let callCount = 0;
  const fetchImpl = async () => {
    callCount += 1;
    return mockResponse({ ok: false, status: 503, body: 'down' });
  };
  const client = createRestClient({
    baseUrl: 'https://example.test',
    fetchImpl,
    sleepImpl: instantSleep,
    retryCount: 2,
  });

  await assert.rejects(() => client.publicGet('/fapi/v1/time'), /Binance REST error 503/);
  assert.equal(callCount, 3); // percobaan awal + 2 retry
});

test('signedRequest: menolak tanpa apiKey/apiSecret (fail-safe)', async () => {
  const client = createRestClient({ baseUrl: 'https://example.test', fetchImpl: async () => mockResponse({}) });
  await assert.rejects(() => client.signedRequest('POST', '/fapi/v1/order', {}), /belum diisi/);
});

test('signedRequest: menyertakan header X-MBX-APIKEY dan query yang sudah ditandatangani', async () => {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url, init });
    return mockResponse({ body: { orderId: 1 } });
  };
  const client = createRestClient({
    baseUrl: 'https://example.test',
    apiKey: 'my-key',
    apiSecret: 'my-secret',
    fetchImpl,
    sleepImpl: instantSleep,
  });

  await client.signedRequest('POST', '/fapi/v1/order', { symbol: 'BTCUSDT', side: 'BUY' });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.method, 'POST');
  assert.equal(calls[0].init.headers['X-MBX-APIKEY'], 'my-key');
  assert.ok(calls[0].url.includes('symbol=BTCUSDT&side=BUY&timestamp='));
  assert.ok(calls[0].url.includes('&recvWindow=5000'));
  assert.ok(calls[0].url.includes('&signature='));
  // Tidak pernah membocorkan apiSecret mentah di URL
  assert.ok(!calls[0].url.includes('my-secret'));
});

test('jaringan gagal (reject) di-retry lalu berhasil', async () => {
  let callCount = 0;
  const fetchImpl = async () => {
    callCount += 1;
    if (callCount < 2) throw new Error('ECONNRESET simulasi');
    return mockResponse({ body: { ok: true } });
  };
  const client = createRestClient({
    baseUrl: 'https://example.test',
    fetchImpl,
    sleepImpl: instantSleep,
    retryCount: 2,
  });

  const result = await client.publicGet('/fapi/v1/time');
  assert.equal(callCount, 2);
  assert.deepEqual(result, { ok: true });
});
