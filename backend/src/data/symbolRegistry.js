'use strict';

/**
 * Symbol registry -- parsing & screening dasar hasil `GET /fapi/v1/exchangeInfo`.
 *
 * Catatan penting (diverifikasi dari dokumentasi resmi 2026-11-07, lihat
 * docs/API_SPEC.md): filter MIN_NOTIONAL di USDⓈ-M Futures memakai field
 * "notional", BUKAN "minNotional" seperti di Spot. Kalau ini salah dibaca,
 * perhitungan ukuran posisi minimum di Fase 5/6 akan salah tanpa ada error
 * yang kelihatan -- jadi field mapping di bawah ini harus dijaga konsisten
 * dengan dokumentasi resmi, bukan diasumsikan sama dengan Spot.
 */

function parseSymbolInfo(raw) {
  const filters = {};
  for (const f of raw.filters || []) {
    filters[f.filterType] = f;
  }

  return Object.freeze({
    symbol: raw.symbol,
    pair: raw.pair,
    contractType: raw.contractType, // 'PERPETUAL' | 'CURRENT_QUARTER' | 'NEXT_QUARTER' | dst.
    status: raw.status, // 'TRADING' | 'PENDING_TRADING' | 'SETTLING' | dst.
    baseAsset: raw.baseAsset,
    quoteAsset: raw.quoteAsset,
    pricePrecision: raw.pricePrecision,
    quantityPrecision: raw.quantityPrecision,
    tickSize: filters.PRICE_FILTER ? Number(filters.PRICE_FILTER.tickSize) : null,
    minPrice: filters.PRICE_FILTER ? Number(filters.PRICE_FILTER.minPrice) : null,
    maxPrice: filters.PRICE_FILTER ? Number(filters.PRICE_FILTER.maxPrice) : null,
    stepSize: filters.LOT_SIZE ? Number(filters.LOT_SIZE.stepSize) : null,
    minQty: filters.LOT_SIZE ? Number(filters.LOT_SIZE.minQty) : null,
    maxQty: filters.LOT_SIZE ? Number(filters.LOT_SIZE.maxQty) : null,
    // Futures: field "notional" (BUKAN "minNotional" seperti Spot) -- lihat catatan di atas.
    minNotional: filters.MIN_NOTIONAL ? Number(filters.MIN_NOTIONAL.notional) : null,
  });
}

function parseExchangeInfo(raw) {
  if (!raw || !Array.isArray(raw.symbols)) {
    throw new Error('parseExchangeInfo: payload tidak valid -- field "symbols" hilang atau bukan array');
  }
  return raw.symbols.map(parseSymbolInfo);
}

/** Hanya kontrak PERPETUAL yang sedang TRADING -- lihat PROJECT_MASTER.md §Keputusan Default. */
function filterTradablePerpetuals(symbols) {
  return symbols.filter((s) => s.status === 'TRADING' && s.contractType === 'PERPETUAL');
}

/** Simbol yang ada di `currentSymbols` tapi tidak ada di `previousSymbols`. */
function detectNewSymbols(previousSymbols, currentSymbols) {
  const previousSet = new Set(previousSymbols.map((s) => s.symbol));
  return currentSymbols.filter((s) => !previousSet.has(s.symbol));
}

/**
 * Validasi quantity/price terhadap filter sebuah simbol -- dipakai Fase 5/6
 * sebelum mengirim order supaya tidak ditolak Binance. Mengembalikan array
 * alasan pelanggaran (kosong = valid).
 */
function validateOrderAgainstFilters(symbolInfo, { price, quantity }) {
  const violations = [];
  if (price != null && symbolInfo.tickSize) {
    if (price < symbolInfo.minPrice || price > symbolInfo.maxPrice) {
      violations.push(`price ${price} di luar rentang [${symbolInfo.minPrice}, ${symbolInfo.maxPrice}]`);
    }
    const steps = (price - symbolInfo.minPrice) / symbolInfo.tickSize;
    if (Math.abs(steps - Math.round(steps)) > 1e-8) {
      violations.push(`price ${price} tidak kelipatan tickSize ${symbolInfo.tickSize}`);
    }
  }
  if (quantity != null && symbolInfo.stepSize) {
    if (quantity < symbolInfo.minQty || quantity > symbolInfo.maxQty) {
      violations.push(`quantity ${quantity} di luar rentang [${symbolInfo.minQty}, ${symbolInfo.maxQty}]`);
    }
    const steps = (quantity - symbolInfo.minQty) / symbolInfo.stepSize;
    if (Math.abs(steps - Math.round(steps)) > 1e-8) {
      violations.push(`quantity ${quantity} tidak kelipatan stepSize ${symbolInfo.stepSize}`);
    }
  }
  if (price != null && quantity != null && symbolInfo.minNotional != null) {
    const notional = price * quantity;
    if (notional < symbolInfo.minNotional) {
      violations.push(`notional ${notional} di bawah minNotional ${symbolInfo.minNotional}`);
    }
  }
  return violations;
}

module.exports = {
  parseSymbolInfo,
  parseExchangeInfo,
  filterTradablePerpetuals,
  detectNewSymbols,
  validateOrderAgainstFilters,
};
