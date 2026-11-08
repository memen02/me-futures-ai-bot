'use strict';

/**
 * Rolling window candle untuk SATU simbol+timeframe. Tidak pernah menyimpan
 * lebih dari `maxSize` candle -- inti dari strategi hemat RAM di HP 4GB
 * (lihat PROJECT_MASTER.md §Desain ME Smart Resource Manager).
 */
class RollingWindow {
  constructor(maxSize) {
    if (!Number.isInteger(maxSize) || maxSize <= 0) {
      throw new Error('RollingWindow: maxSize harus bilangan bulat positif');
    }
    this.maxSize = maxSize;
    this.candles = []; // urut menaik berdasarkan openTime, HANYA candle closed
  }

  /** Hanya dipanggil untuk candle yang SUDAH closed (lihat parseKlineEvent). */
  push(candle) {
    const last = this.candles[this.candles.length - 1];
    if (last && last.openTime === candle.openTime) {
      // Re-push candle closed dengan openTime sama (jarang, tapi jaga-jaga) -> ganti, jangan dobel.
      this.candles[this.candles.length - 1] = candle;
    } else {
      this.candles.push(candle);
    }
    if (this.candles.length > this.maxSize) {
      this.candles.shift();
    }
  }

  getAll() {
    return this.candles.slice();
  }

  getLast(n = 1) {
    return this.candles.slice(-n);
  }

  size() {
    return this.candles.length;
  }
}

/**
 * Payload event kline dari WS Binance Futures: `event.k` berisi
 * { t: openTime, T: closeTime, i: interval, x: isClosed, o,h,l,c,v, n: numTrades }.
 * Mengembalikan `null` jika candle BELUM closed (x !== true) -- sesuai aturan
 * "gunakan candle yang sudah tertutup" di spesifikasi awal proyek.
 */
function parseKlineEvent(event) {
  const k = event && event.k;
  if (!k) return null;
  if (k.x !== true) return null;
  return Object.freeze({
    symbol: event.s,
    interval: k.i,
    openTime: k.t,
    closeTime: k.T,
    open: Number(k.o),
    high: Number(k.h),
    low: Number(k.l),
    close: Number(k.c),
    volume: Number(k.v),
    quoteVolume: Number(k.q),
    numberOfTrades: k.n,
  });
}

/** Ubah 1 baris REST `GET /fapi/v1/klines` (array of array) jadi objek candle yang sama bentuknya. */
function parseRestKline(row, symbol, interval) {
  const [openTime, open, high, low, close, volume, closeTime, quoteVolume, numberOfTrades] = row;
  return Object.freeze({
    symbol,
    interval,
    openTime,
    closeTime,
    open: Number(open),
    high: Number(high),
    low: Number(low),
    close: Number(close),
    volume: Number(volume),
    quoteVolume: Number(quoteVolume),
    numberOfTrades,
  });
}

/** Mengelola banyak RollingWindow sekaligus, dikunci per (symbol, interval). */
class CandleStoreManager {
  constructor({ maxCandlesPerSeries = 500 } = {}) {
    this.maxCandlesPerSeries = maxCandlesPerSeries;
    this.windows = new Map(); // key: "SYMBOL:interval" -> RollingWindow
  }

  _key(symbol, interval) {
    return `${symbol}:${interval}`;
  }

  _windowFor(symbol, interval) {
    const key = this._key(symbol, interval);
    if (!this.windows.has(key)) {
      this.windows.set(key, new RollingWindow(this.maxCandlesPerSeries));
    }
    return this.windows.get(key);
  }

  /** Masukkan hasil WS kline event. Mengembalikan candle yang disimpan, atau null jika belum closed. */
  ingestKlineEvent(event) {
    const candle = parseKlineEvent(event);
    if (!candle) return null;
    this._windowFor(candle.symbol, candle.interval).push(candle);
    return candle;
  }

  /** Backfill dari REST klines (dipakai saat start, sebelum WS live mulai mengalir). */
  backfillFromRest(symbol, interval, restKlineRows) {
    const window = this._windowFor(symbol, interval);
    for (const row of restKlineRows) {
      window.push(parseRestKline(row, symbol, interval));
    }
    return window.size();
  }

  getSeries(symbol, interval) {
    const key = this._key(symbol, interval);
    return this.windows.has(key) ? this.windows.get(key).getAll() : [];
  }

  seriesCount() {
    return this.windows.size;
  }
}

module.exports = { RollingWindow, parseKlineEvent, parseRestKline, CandleStoreManager };
