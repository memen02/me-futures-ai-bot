'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
  MarketSnapshotStore,
  parseTickerArrItem,
  parseMarkPriceArrItem,
  parseForceOrderEvent,
} = require('../src/data/marketSnapshot');

test('upsertTicker/getTicker: roundtrip dasar', () => {
  const store = new MarketSnapshotStore();
  store.upsertTicker('BTCUSDT', { lastPrice: 50000 });
  const entry = store.getTicker('BTCUSDT');
  assert.equal(entry.lastPrice, 50000);
  assert.ok(typeof entry.updatedAt === 'number');
});

test('data basi (melewati ttlMs) dianggap tidak ada (null) -- clock disuntik supaya deterministik', () => {
  let currentTime = 1_000_000;
  const store = new MarketSnapshotStore({ ttlMs: 500, now: () => currentTime });

  store.upsertTicker('BTCUSDT', { lastPrice: 50000 });
  assert.ok(store.getTicker('BTCUSDT'), 'baru di-set, harus fresh');

  currentTime += 1000; // lewati ttl 500ms
  assert.equal(store.getTicker('BTCUSDT'), null, 'setelah > ttlMs tanpa update baru, harus dianggap basi');
});

test('data yang terus diperbarui sebelum ttl habis tetap fresh', () => {
  let currentTime = 0;
  const store = new MarketSnapshotStore({ ttlMs: 1000, now: () => currentTime });

  store.upsertTicker('BTCUSDT', { lastPrice: 1 });
  currentTime += 800;
  store.upsertTicker('BTCUSDT', { lastPrice: 2 }); // refresh sebelum basi
  currentTime += 800; // total 1600ms sejak awal, tapi cuma 800ms sejak refresh terakhir

  const entry = store.getTicker('BTCUSDT');
  assert.ok(entry, 'harus tetap fresh karena di-refresh sebelum ttl habis');
  assert.equal(entry.lastPrice, 2);
});

test('getAllFreshTickers: hanya mengembalikan yang masih fresh', () => {
  let currentTime = 0;
  const store = new MarketSnapshotStore({ ttlMs: 500, now: () => currentTime });
  store.upsertTicker('BTCUSDT', { lastPrice: 1 });
  currentTime += 1000;
  store.upsertTicker('ETHUSDT', { lastPrice: 2 }); // di-set setelah BTCUSDT basi

  const fresh = store.getAllFreshTickers();
  assert.deepEqual(fresh.map((f) => f.symbol), ['ETHUSDT']);
});

test('pushLiquidation + getRecentLiquidations: filter berdasarkan waktu, dan dibatasi jumlah maksimum', () => {
  let currentTime = 0;
  const store = new MarketSnapshotStore({ maxLiquidations: 3, now: () => currentTime });
  for (let i = 0; i < 5; i += 1) {
    store.pushLiquidation({ symbol: 'BTCUSDT', i });
    currentTime += 1;
  }
  // Dibatasi maxLiquidations=3 -> hanya 3 terakhir (i=2,3,4) yang tersisa
  assert.equal(store.liquidations.length, 3);
  assert.deepEqual(store.liquidations.map((l) => l.i), [2, 3, 4]);
});

test('parseTickerArrItem: field mapping sesuai dokumentasi (c,v,q,P)', () => {
  const parsed = parseTickerArrItem({ c: '50000.5', v: '1234.5', q: '987654.3', P: '2.5' });
  assert.deepEqual(parsed, { lastPrice: 50000.5, volume24h: 1234.5, quoteVolume24h: 987654.3, priceChangePercent: 2.5 });
});

test('parseMarkPriceArrItem: field mapping sesuai dokumentasi (p,i,r,T)', () => {
  const parsed = parseMarkPriceArrItem({ p: '50010.0', i: '50005.0', r: '0.0001', T: 1700000000000 });
  assert.deepEqual(parsed, { markPrice: 50010.0, indexPrice: 50005.0, fundingRate: 0.0001, nextFundingTime: 1700000000000 });
});

test('parseForceOrderEvent: field mapping nested object "o" sesuai dokumentasi', () => {
  const event = {
    e: 'forceOrder',
    E: 1700000000000,
    o: { s: 'BTCUSDT', S: 'SELL', p: '49000', q: '0.5', X: 'FILLED' },
  };
  const parsed = parseForceOrderEvent(event);
  assert.deepEqual(parsed, {
    symbol: 'BTCUSDT',
    side: 'SELL',
    price: 49000,
    quantity: 0.5,
    orderStatus: 'FILLED',
    eventTime: 1700000000000,
  });
});
