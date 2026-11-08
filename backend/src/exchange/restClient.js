'use strict';

const { buildQueryString, signQueryString } = require('./signer');

const DEFAULT_TIMEOUT_MS = 10_000;
const DEFAULT_RETRY_COUNT = 2;
const DEFAULT_RECV_WINDOW = 5000;
// Status yang masuk akal untuk dicoba ulang (transient). 4XX selain ini TIDAK diulang --
// itu kesalahan di sisi kita (parameter salah dsb), mengulang tidak akan membantu.
const RETRYABLE_STATUS = new Set([408, 429, 500, 502, 503, 504]);

/**
 * Factory REST client Binance Futures. `fetchImpl` & `sleepImpl` disuntikkan
 * supaya seluruh logika retry/backoff bisa diuji tanpa jaringan nyata maupun
 * benar-benar menunggu (lihat test/restClient.test.js).
 */
function createRestClient(options = {}) {
  const {
    baseUrl,
    apiKey = '',
    apiSecret = '',
    fetchImpl = globalThis.fetch,
    sleepImpl = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
    timeoutMs = DEFAULT_TIMEOUT_MS,
    retryCount = DEFAULT_RETRY_COUNT,
    logger = null,
  } = options;

  if (!baseUrl) throw new Error('createRestClient: baseUrl wajib diisi');
  if (!fetchImpl) throw new Error('createRestClient: fetch tidak tersedia di runtime ini');

  function backoffMs(attempt) {
    return 300 * 2 ** attempt;
  }

  async function doFetch(url, init, attempt = 0) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    let res;
    try {
      res = await fetchImpl(url, { ...init, signal: controller.signal });
    } catch (err) {
      clearTimeout(timer);
      const isAbort = err && err.name === 'AbortError';
      if (attempt < retryCount) {
        logger?.warn({ url, attempt, err: String(err) }, isAbort ? 'request timeout, retry' : 'request gagal, retry');
        await sleepImpl(backoffMs(attempt));
        return doFetch(url, init, attempt + 1);
      }
      throw err;
    }
    clearTimeout(timer);

    if (!res.ok && RETRYABLE_STATUS.has(res.status) && attempt < retryCount) {
      logger?.warn({ url, status: res.status, attempt }, 'status transient, retry');
      await sleepImpl(backoffMs(attempt));
      return doFetch(url, init, attempt + 1);
    }
    return res;
  }

  async function parseResponse(res) {
    const text = await res.text();
    let body = null;
    if (text) {
      try {
        body = JSON.parse(text);
      } catch {
        body = text;
      }
    }
    if (!res.ok) {
      const err = new Error(`Binance REST error ${res.status}: ${typeof body === 'string' ? body : JSON.stringify(body)}`);
      err.status = res.status;
      err.body = body;
      throw err;
    }
    return body;
  }

  /** GET endpoint publik (tidak butuh API key/signature), mis. exchangeInfo, klines, ping. */
  async function publicGet(path, params = {}) {
    const qs = buildQueryString(params);
    const url = `${baseUrl}${path}${qs ? `?${qs}` : ''}`;
    const res = await doFetch(url, { method: 'GET' });
    return parseResponse(res);
  }

  /** Request SIGNED (butuh API key + HMAC signature) -- untuk endpoint TRADE/USER_DATA. */
  async function signedRequest(method, path, params = {}) {
    if (!apiKey || !apiSecret) {
      throw new Error(
        'signedRequest: BINANCE_API_KEY/BINANCE_API_SECRET belum diisi -- tidak bisa memanggil endpoint signed. ' +
          'Isi di .env (dev) atau lewat APK (produksi, Fase 10). Lihat docs/SECURITY.md.'
      );
    }
    const fullParams = {
      ...params,
      timestamp: Date.now(),
      recvWindow: params.recvWindow ?? DEFAULT_RECV_WINDOW,
    };
    const signedQs = signQueryString(fullParams, apiSecret);
    const url = `${baseUrl}${path}?${signedQs}`;
    const res = await doFetch(url, { method, headers: { 'X-MBX-APIKEY': apiKey } });
    return parseResponse(res);
  }

  return { publicGet, signedRequest, baseUrl };
}

module.exports = { createRestClient };
