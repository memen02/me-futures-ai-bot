'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { RollingWindow, parseKlineEvent, parseRestKline, CandleStoreManager } = require('../src/data/candleStore');

function fakeKlineEvent({ openTime, closed, close = '100.0' }) {
  return {
    e: 'kline',
    s: 'BTCUSDT',
    k: {
      t: openTime,
      T: openTime + 59_999,
      i: '1m',
      x: closed,
      o: '99.5',
      h: '101.0',
      l: '99.0',
      c: close,
      v: '12.345',
      q: '1234.5',
      n: 42,
    },
  };
}

test('RollingWindow: menolak maxSize tidak valid', () => {
  assert.throws(() => new RollingWindow(0), /maxSize harus bilangan bulat positif/);
  assert.throws(() => new RollingWindow(-1), /maxSize harus bilangan bulat positif/);
});

test('RollingWindow: membuang candle terlama saat melebihi maxSize (inti hemat RAM)', () => {
  const win = new RollingWindow(3);
  for (let i = 0; i < 5; i += 1) {
    win.push({ openTime: i, close: i });
  }
  assert.equal(win.size(), 3);
  assert.deepEqual(win.getAll().map((c) => c.openTime), [2, 3, 4]); // 0 dan 1 sudah dibuang
});

test('RollingWindow.getLast: mengembalikan n candle terakhir', () => {
  const win = new RollingWindow(10);
  for (let i = 0; i < 5; i += 1) win.push({ openTime: i });
  assert.deepEqual(win.getLast(2).map((c) => c.openTime), [3, 4]);
});

test('parseKlineEvent: candle BELUM closed (x:false) dikembalikan null (aturan "candle tertutup")', () => {
  const event = fakeKlineEvent({ openTime: 1000, closed: false });
  assert.equal(parseKlineEvent(event), null);
});

test('parseKlineEvent: candle closed (x:true) di-parse dengan benar', () => {
  const event = fakeKlineEvent({ openTime: 1000, closed: true, close: '105.5' });
  const candle = parseKlineEvent(event);
  assert.equal(candle.symbol, 'BTCUSDT');
  assert.equal(candle.interval, '1m');
  assert.equal(candle.openTime, 1000);
  assert.equal(candle.close, 105.5);
  assert.equal(typeof candle.close, 'number'); // harus dikonversi dari string
});

test('parseRestKline: parsing array mentah REST /fapi/v1/klines', () => {
  const row = [1000, '99.5', '101.0', '99.0', '100.0', '12.345', 59999, '1234.5', 42];
  const candle = parseRestKline(row, 'ETHUSDT', '5m');
  assert.equal(candle.symbol, 'ETHUSDT');
  assert.equal(candle.interval, '5m');
  assert.equal(candle.open, 99.5);
  assert.equal(candle.close, 100.0);
});

test('CandleStoreManager: ingestKlineEvent mengabaikan candle belum closed, menyimpan yang sudah closed', () => {
  const mgr = new CandleStoreManager({ maxCandlesPerSeries: 10 });
  assert.equal(mgr.ingestKlineEvent(fakeKlineEvent({ openTime: 1, closed: false })), null);
  assert.equal(mgr.getSeries('BTCUSDT', '1m').length, 0);

  const saved = mgr.ingestKlineEvent(fakeKlineEvent({ openTime: 1, closed: true }));
  assert.ok(saved);
  assert.equal(mgr.getSeries('BTCUSDT', '1m').length, 1);
});

test('CandleStoreManager: memisahkan seri per symbol DAN per interval (tidak saling bocor)', () => {
  const mgr = new CandleStoreManager();
  mgr.ingestKlineEvent(fakeKlineEvent({ openTime: 1, closed: true }));
  const ethEvent = { ...fakeKlineEvent({ openTime: 1, closed: true }), s: 'ETHUSDT' };
  ethEvent.k = { ...ethEvent.k };
  mgr.ingestKlineEvent(ethEvent);

  assert.equal(mgr.getSeries('BTCUSDT', '1m').length, 1);
  assert.equal(mgr.getSeries('ETHUSDT', '1m').length, 1);
  assert.equal(mgr.getSeries('BTCUSDT', '5m').length, 0); // interval beda, seri beda
  assert.equal(mgr.seriesCount(), 2);
});

test('CandleStoreManager.backfillFromRest: mengisi window dari data historis REST', () => {
  const mgr = new CandleStoreManager({ maxCandlesPerSeries: 500 });
  const rows = Array.from({ length: 10 }, (_, i) => [
    i * 60_000, '100', '101', '99', '100.5', '10', i * 60_000 + 59999, '1005', 5,
  ]);
  const count = mgr.backfillFromRest('BTCUSDT', '1m', rows);
  assert.equal(count, 10);
  assert.equal(mgr.getSeries('BTCUSDT', '1m').length, 10);
});

test('CandleStoreManager: backfill lalu lanjut live WS tidak duplikat candle dengan openTime sama', () => {
  const mgr = new CandleStoreManager({ maxCandlesPerSeries: 500 });
  mgr.backfillFromRest('BTCUSDT', '1m', [[1000, '1', '1', '1', '1', '1', 59999, '1', 1]]);
  mgr.ingestKlineEvent(fakeKlineEvent({ openTime: 1000, closed: true, close: '999' }));
  const series = mgr.getSeries('BTCUSDT', '1m');
  assert.equal(series.length, 1, 'candle dengan openTime sama tidak boleh dobel');
  assert.equal(series[0].close, 999, 'candle terbaru dengan openTime sama harus menggantikan yang lama');
});
