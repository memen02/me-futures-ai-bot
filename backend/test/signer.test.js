'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { buildQueryString, hmacSha256Hex, signQueryString } = require('../src/exchange/signer');

test('hmacSha256Hex cocok dengan test vector HMAC-SHA256 yang dikenal luas (independen dari implementasi kita)', () => {
  // key="key", message="The quick brown fox jumps over the lazy dog"
  const expected = 'f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8';
  const actual = hmacSha256Hex('The quick brown fox jumps over the lazy dog', 'key');
  assert.equal(actual, expected);
  assert.equal(actual.length, 64); // SHA256 = 32 byte = 64 hex char
});

test('buildQueryString: urutan key dipertahankan, value di-encode, undefined/null dilewati', () => {
  const qs = buildQueryString({ symbol: 'BTCUSDT', side: 'BUY', price: undefined, note: null, qty: 1 });
  assert.equal(qs, 'symbol=BTCUSDT&side=BUY&qty=1');
});

test('buildQueryString: object kosong menghasilkan string kosong', () => {
  assert.equal(buildQueryString({}), '');
});

test('signQueryString: hasil akhir berbentuk "<qs>&signature=<hex64>" dan konsisten (deterministik)', () => {
  const params = { symbol: 'BTCUSDT', side: 'BUY', type: 'MARKET', quantity: '0.01', timestamp: 1700000000000 };
  const signed = signQueryString(params, 'super-secret');
  const [qsPart, sigPart] = signed.split('&signature=');
  assert.equal(qsPart, 'symbol=BTCUSDT&side=BUY&type=MARKET&quantity=0.01&timestamp=1700000000000');
  assert.equal(sigPart.length, 64);
  // deterministik: secret & params sama -> signature sama
  const signedAgain = signQueryString(params, 'super-secret');
  assert.equal(signed, signedAgain);
});

test('signQueryString: secret berbeda menghasilkan signature berbeda (tidak bocor/collision sepele)', () => {
  const params = { symbol: 'BTCUSDT', timestamp: 1700000000000 };
  const signedA = signQueryString(params, 'secret-a');
  const signedB = signQueryString(params, 'secret-b');
  assert.notEqual(signedA, signedB);
});

test('signQueryString: melempar error jika apiSecret kosong (fail-safe, jangan kirim request tak tertandatangani)', () => {
  assert.throws(() => signQueryString({ symbol: 'BTCUSDT' }, ''), /apiSecret wajib diisi/);
});
