'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { buildCombinedStreamUrl, computeBackoffMs, ReconnectingStream } = require('../src/exchange/wsClient');

test('buildCombinedStreamUrl: menggabungkan beberapa stream & lowercase simbol', () => {
  const url = buildCombinedStreamUrl('wss://fstream.binance.com/market', ['BTCUSDT@markPrice', 'ethusdt@kline_1m']);
  assert.equal(url, 'wss://fstream.binance.com/market/stream?streams=btcusdt@markprice/ethusdt@kline_1m');
});

test('buildCombinedStreamUrl: melempar jika streams kosong', () => {
  assert.throws(() => buildCombinedStreamUrl('wss://x', []), /tidak boleh kosong/);
});

test('computeBackoffMs: naik seiring attempt dan dibatasi maxMs', () => {
  const a0 = computeBackoffMs(0, { baseMs: 1000, maxMs: 30_000 });
  const a3 = computeBackoffMs(3, { baseMs: 1000, maxMs: 30_000 });
  const aHigh = computeBackoffMs(20, { baseMs: 1000, maxMs: 30_000 });
  assert.ok(a0 >= 1000 && a0 <= 1300); // base + jitter maks 30%
  assert.ok(a3 > a0, 'backoff harus naik seiring attempt bertambah');
  assert.ok(aHigh <= 30_000 * 1.3, 'tidak boleh tumbuh tak terbatas, harus dibatasi maxMs (+jitter)');
});

/** WebSocket palsu minimal (EventTarget-like) untuk menguji ReconnectingStream tanpa jaringan nyata. */
class FakeWebSocket {
  constructor(url) {
    this.url = url;
    this.listeners = {};
    FakeWebSocket.instances.push(this);
  }
  addEventListener(type, handler) {
    (this.listeners[type] ||= []).push(handler);
  }
  close() {
    this._emit('close', {});
  }
  _emit(type, event) {
    for (const handler of this.listeners[type] || []) handler(event);
  }
}
FakeWebSocket.instances = [];

test('ReconnectingStream: onOpen/onMessage terpanggil, pesan JSON di-parse', () => {
  FakeWebSocket.instances = [];
  const received = [];
  let opened = false;

  const stream = new ReconnectingStream({
    url: 'wss://fake/test',
    WebSocketImpl: FakeWebSocket,
    onOpen: () => { opened = true; },
    onMessage: (data) => received.push(data),
  }).connect();

  const fake = FakeWebSocket.instances[0];
  fake._emit('open', {});
  fake._emit('message', { data: JSON.stringify({ e: 'markPriceUpdate', s: 'BTCUSDT' }) });

  assert.equal(opened, true);
  assert.deepEqual(received, [{ e: 'markPriceUpdate', s: 'BTCUSDT' }]);
  stream.close();
});

test('ReconnectingStream: pesan bukan JSON valid dilewati tanpa melempar', () => {
  FakeWebSocket.instances = [];
  const received = [];
  const stream = new ReconnectingStream({
    url: 'wss://fake/test',
    WebSocketImpl: FakeWebSocket,
    onMessage: (data) => received.push(data),
  }).connect();

  const fake = FakeWebSocket.instances[0];
  assert.doesNotThrow(() => fake._emit('message', { data: 'bukan-json{{{' }));
  assert.equal(received.length, 0);
  stream.close();
});

test('ReconnectingStream: close terputus TIDAK terjadwal (close() oleh user)', () => {
  FakeWebSocket.instances = [];
  let scheduled = false;
  const stream = new ReconnectingStream({
    url: 'wss://fake/test',
    WebSocketImpl: FakeWebSocket,
    scheduleImpl: () => { scheduled = true; },
  }).connect();

  stream.close(); // user yang menutup
  assert.equal(scheduled, false, 'close() oleh user tidak boleh memicu reconnect');
});

test('ReconnectingStream: koneksi terputus TIDAK disengaja -> reconnect dijadwalkan', () => {
  FakeWebSocket.instances = [];
  let scheduledDelay = null;
  const stream = new ReconnectingStream({
    url: 'wss://fake/test',
    WebSocketImpl: FakeWebSocket,
    scheduleImpl: (fn, delay) => { scheduledDelay = delay; },
  }).connect();

  const fake = FakeWebSocket.instances[0];
  fake._emit('close', {}); // server yang memutus, bukan stream.close()

  assert.ok(typeof scheduledDelay === 'number' && scheduledDelay > 0, 'reconnect harus dijadwalkan dengan delay > 0');
});
