'use strict';

/**
 * Market Snapshot Store -- "Tier 1" di rekomendasi Fase 2: data RINGAN untuk
 * SEMUA simbol sekaligus (dari stream !ticker@arr, !markPrice@arr@1s,
 * !forceOrder@arr -- 1 koneksi WS /market mencakup semuanya, lihat
 * src/exchange/endpoints.js). Dipakai Fase 3 (Market Scanner) untuk
 * menyaring simbol mana yang layak dianalisis detail (Tier 2: candle
 * multi-timeframe) -- logika ranking/shortlist-nya sendiri BELUM ada di
 * sini, itu memang scope Fase 3, bukan Fase 2 (lihat docs/TODO.md).
 *
 * Data dianggap basi (stale) setelah `ttlMs` sejak update terakhir -- supaya
 * modul lain tidak pernah mengambil keputusan dari data yang sudah lama diam
 * (misal WS putus tanpa reconnect berhasil).
 */

const DEFAULT_TTL_MS = 60_000;
const DEFAULT_MAX_LIQUIDATIONS = 1000;

class MarketSnapshotStore {
  constructor({ ttlMs = DEFAULT_TTL_MS, maxLiquidations = DEFAULT_MAX_LIQUIDATIONS, now = () => Date.now() } = {}) {
    this.ttlMs = ttlMs;
    this.maxLiquidations = maxLiquidations;
    this.now = now;
    this.ticker = new Map(); // symbol -> { ...data, updatedAt }
    this.markPrice = new Map(); // symbol -> { ...data, updatedAt }
    this.liquidations = []; // array, dibatasi maxLiquidations (bukan rolling window waktu, tapi jumlah)
  }

  upsertTicker(symbol, data) {
    this.ticker.set(symbol, { ...data, updatedAt: this.now() });
  }

  upsertMarkPrice(symbol, data) {
    this.markPrice.set(symbol, { ...data, updatedAt: this.now() });
  }

  pushLiquidation(event) {
    this.liquidations.push({ ...event, receivedAt: this.now() });
    if (this.liquidations.length > this.maxLiquidations) {
      this.liquidations.shift();
    }
  }

  getTicker(symbol) {
    return this._freshOrNull(this.ticker.get(symbol));
  }

  getMarkPrice(symbol) {
    return this._freshOrNull(this.markPrice.get(symbol));
  }

  /** Semua ticker yang masih "fresh" (dalam ttlMs) -- dipakai Fase 3 untuk screening awal. */
  getAllFreshTickers() {
    const result = [];
    for (const [symbol, entry] of this.ticker.entries()) {
      const fresh = this._freshOrNull(entry);
      if (fresh) result.push({ symbol, ...fresh });
    }
    return result;
  }

  getRecentLiquidations(sinceMs = this.ttlMs) {
    const cutoff = this.now() - sinceMs;
    return this.liquidations.filter((l) => l.receivedAt >= cutoff);
  }

  _freshOrNull(entry) {
    if (!entry) return null;
    if (this.now() - entry.updatedAt > this.ttlMs) return null;
    return entry;
  }

  symbolCount() {
    return this.ticker.size;
  }
}

/** Parser 1 item dari stream !ticker@arr (lihat docs/API_SPEC.md untuk field mapping). */
function parseTickerArrItem(item) {
  return {
    lastPrice: Number(item.c),
    volume24h: Number(item.v),
    quoteVolume24h: Number(item.q),
    priceChangePercent: Number(item.P),
  };
}

/** Parser 1 item dari stream !markPrice@arr@1s. */
function parseMarkPriceArrItem(item) {
  return {
    markPrice: Number(item.p),
    indexPrice: Number(item.i),
    fundingRate: Number(item.r),
    nextFundingTime: item.T,
  };
}

/** Parser event forceOrder (liquidasi) tunggal, baik dari !forceOrder@arr maupun <symbol>@forceOrder. */
function parseForceOrderEvent(event) {
  const o = event.o || {};
  return {
    symbol: o.s,
    side: o.S,
    price: Number(o.p),
    quantity: Number(o.q),
    orderStatus: o.X,
    eventTime: event.E,
  };
}

module.exports = {
  MarketSnapshotStore,
  parseTickerArrItem,
  parseMarkPriceArrItem,
  parseForceOrderEvent,
};
