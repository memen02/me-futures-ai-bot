'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
  parseExchangeInfo,
  filterTradablePerpetuals,
  detectNewSymbols,
  validateOrderAgainstFilters,
} = require('../src/data/symbolRegistry');

/** Fixture meniru bentuk nyata respons GET /fapi/v1/exchangeInfo (field-field penting saja). */
const FIXTURE_EXCHANGE_INFO = {
  symbols: [
    {
      symbol: 'BTCUSDT',
      pair: 'BTCUSDT',
      contractType: 'PERPETUAL',
      status: 'TRADING',
      baseAsset: 'BTC',
      quoteAsset: 'USDT',
      pricePrecision: 2,
      quantityPrecision: 3,
      filters: [
        { filterType: 'PRICE_FILTER', minPrice: '0.10', maxPrice: '1000000', tickSize: '0.10' },
        { filterType: 'LOT_SIZE', minQty: '0.001', maxQty: '1000', stepSize: '0.001' },
        { filterType: 'MIN_NOTIONAL', notional: '5.0' }, // Futures: field "notional", bukan "minNotional"
      ],
    },
    {
      symbol: 'ETHUSDT',
      pair: 'ETHUSDT',
      contractType: 'PERPETUAL',
      status: 'TRADING',
      baseAsset: 'ETH',
      quoteAsset: 'USDT',
      pricePrecision: 2,
      quantityPrecision: 3,
      filters: [
        { filterType: 'PRICE_FILTER', minPrice: '0.01', maxPrice: '100000', tickSize: '0.01' },
        { filterType: 'LOT_SIZE', minQty: '0.001', maxQty: '10000', stepSize: '0.001' },
        { filterType: 'MIN_NOTIONAL', notional: '5.0' },
      ],
    },
    {
      // Kontrak quarterly -- harus TERFILTER OUT oleh filterTradablePerpetuals (Keputusan Default: PERPETUAL saja)
      symbol: 'BTCUSD_250328',
      pair: 'BTCUSD',
      contractType: 'CURRENT_QUARTER',
      status: 'TRADING',
      baseAsset: 'BTC',
      quoteAsset: 'USD',
      filters: [],
    },
    {
      // Sedang delisting -- harus terfilter out juga
      symbol: 'OLDCOINUSDT',
      pair: 'OLDCOINUSDT',
      contractType: 'PERPETUAL',
      status: 'SETTLING',
      baseAsset: 'OLDCOIN',
      quoteAsset: 'USDT',
      filters: [],
    },
  ],
};

test('parseExchangeInfo: melempar jika field symbols hilang/bukan array', () => {
  assert.throws(() => parseExchangeInfo({}), /payload tidak valid/);
  assert.throws(() => parseExchangeInfo(null), /payload tidak valid/);
});

test('parseExchangeInfo: parsing field dasar & filter benar, termasuk minNotional dari field "notional"', () => {
  const symbols = parseExchangeInfo(FIXTURE_EXCHANGE_INFO);
  const btc = symbols.find((s) => s.symbol === 'BTCUSDT');
  assert.equal(btc.contractType, 'PERPETUAL');
  assert.equal(btc.tickSize, 0.1);
  assert.equal(btc.stepSize, 0.001);
  assert.equal(btc.minQty, 0.001);
  assert.equal(btc.minNotional, 5.0); // dari field "notional", BUKAN "minNotional"
});

test('filterTradablePerpetuals: hanya PERPETUAL + status TRADING yang lolos', () => {
  const symbols = parseExchangeInfo(FIXTURE_EXCHANGE_INFO);
  const tradable = filterTradablePerpetuals(symbols);
  const names = tradable.map((s) => s.symbol).sort();
  assert.deepEqual(names, ['BTCUSDT', 'ETHUSDT']);
});

test('detectNewSymbols: mendeteksi simbol yang muncul di scan terbaru tapi tidak di scan sebelumnya', () => {
  const symbols = parseExchangeInfo(FIXTURE_EXCHANGE_INFO);
  const previous = symbols.filter((s) => s.symbol !== 'ETHUSDT'); // seolah ETHUSDT baru listing
  const detected = detectNewSymbols(previous, symbols);
  assert.deepEqual(detected.map((s) => s.symbol), ['ETHUSDT']);
});

test('detectNewSymbols: array kosong jika tidak ada simbol baru', () => {
  const symbols = parseExchangeInfo(FIXTURE_EXCHANGE_INFO);
  assert.deepEqual(detectNewSymbols(symbols, symbols), []);
});

test('validateOrderAgainstFilters: order valid (kelipatan tick/step, notional cukup) -> tidak ada pelanggaran', () => {
  const [btc] = parseExchangeInfo(FIXTURE_EXCHANGE_INFO);
  const violations = validateOrderAgainstFilters(btc, { price: 50000.1, quantity: 0.01 });
  assert.deepEqual(violations, []);
});

test('validateOrderAgainstFilters: price bukan kelipatan tickSize -> pelanggaran', () => {
  const [btc] = parseExchangeInfo(FIXTURE_EXCHANGE_INFO);
  const violations = validateOrderAgainstFilters(btc, { price: 50000.17, quantity: 0.01 });
  assert.ok(violations.some((v) => v.includes('tickSize')));
});

test('validateOrderAgainstFilters: quantity di bawah minQty -> pelanggaran', () => {
  const [btc] = parseExchangeInfo(FIXTURE_EXCHANGE_INFO);
  const violations = validateOrderAgainstFilters(btc, { price: 50000, quantity: 0.0001 });
  assert.ok(violations.some((v) => v.includes('quantity') && v.includes('rentang')));
});

test('validateOrderAgainstFilters: notional di bawah minNotional -> pelanggaran', () => {
  const [btc] = parseExchangeInfo(FIXTURE_EXCHANGE_INFO);
  // price * quantity = 1 * 0.001 = 0.001, jauh di bawah minNotional 5.0
  const violations = validateOrderAgainstFilters(btc, { price: 1, quantity: 0.001 });
  assert.ok(violations.some((v) => v.includes('notional')));
});
