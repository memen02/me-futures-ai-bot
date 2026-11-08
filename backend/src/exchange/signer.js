'use strict';

const crypto = require('crypto');

/**
 * Bangun query string dari object params, urutan key sesuai urutan di object
 * (Binance tidak mewajibkan urutan tertentu, yang penting string yang ditanda-
 * tangani SAMA PERSIS dengan string yang dikirim). Melewati key bernilai
 * undefined/null supaya tidak ikut ditandatangani.
 */
function buildQueryString(params = {}) {
  const parts = [];
  for (const key of Object.keys(params)) {
    const value = params[key];
    if (value === undefined || value === null) continue;
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`);
  }
  return parts.join('&');
}

/** HMAC SHA256, hex-encoded -- primitif tanda tangan yang dipakai semua SIGNED endpoint Binance. */
function hmacSha256Hex(message, secret) {
  return crypto.createHmac('sha256', secret).update(message).digest('hex');
}

/**
 * Bangun query string yang sudah ditandatangani: `...&signature=<hmac>`.
 * `params` idealnya sudah menyertakan `timestamp` (dan `recvWindow` opsional)
 * sebelum dipanggil -- fungsi ini tidak menambahkan itu sendiri supaya tetap
 * murni/predictable untuk unit test.
 */
function signQueryString(params, apiSecret) {
  if (!apiSecret) {
    throw new Error('signQueryString: apiSecret wajib diisi');
  }
  const qs = buildQueryString(params);
  const signature = hmacSha256Hex(qs, apiSecret);
  return qs ? `${qs}&signature=${signature}` : `signature=${signature}`;
}

module.exports = { buildQueryString, hmacSha256Hex, signQueryString };
