'use strict';

/**
 * WS client Binance Futures -- pakai `WebSocket` bawaan Node (tersedia default
 * mulai Node 22, lihat package.json "engines") supaya TIDAK butuh dependency
 * `ws` dari npm. Ini sengaja: menghindari risiko gagal compile/install di
 * Termux, dan supaya Fase 2 tetap bisa diuji penuh tanpa jaringan (reconnect
 * logic diuji dengan WebSocketImpl palsu, lihat test/wsClient.test.js).
 */

/** Gabungkan nama-nama stream jadi satu URL combined stream: `${base}/stream?streams=a/b/c`. */
function buildCombinedStreamUrl(baseUrl, streams) {
  if (!baseUrl) throw new Error('buildCombinedStreamUrl: baseUrl wajib diisi');
  if (!Array.isArray(streams) || streams.length === 0) {
    throw new Error('buildCombinedStreamUrl: streams tidak boleh kosong');
  }
  const joined = streams.map((s) => String(s).toLowerCase()).join('/');
  return `${baseUrl}/stream?streams=${joined}`;
}

/**
 * Backoff eksponensial dengan jitter, dibatasi maxMs, supaya reconnect tidak
 * langsung spam ulang dan tidak semua koneksi reconnect barengan.
 */
function computeBackoffMs(attempt, { baseMs = 1000, maxMs = 30_000 } = {}) {
  const exp = Math.min(maxMs, baseMs * 2 ** attempt);
  const jitter = Math.random() * 0.3 * exp;
  return Math.round(exp + jitter);
}

/**
 * Koneksi WS dengan auto-reconnect. Tidak membuka koneksi sampai `.connect()`
 * dipanggil. `WebSocketImpl` disuntikkan supaya bisa diuji tanpa jaringan nyata.
 */
class ReconnectingStream {
  constructor({ url, onMessage, onOpen, onClose, onError, logger = null, WebSocketImpl = globalThis.WebSocket, scheduleImpl = setTimeout }) {
    if (!url) throw new Error('ReconnectingStream: url wajib diisi');
    if (!WebSocketImpl) {
      throw new Error(
        'ReconnectingStream: WebSocket tidak tersedia di runtime ini (butuh Node >=22, atau suntikkan WebSocketImpl dari package `ws`).'
      );
    }
    this.url = url;
    this.onMessage = onMessage;
    this.onOpen = onOpen;
    this.onClose = onClose;
    this.onError = onError;
    this.logger = logger;
    this.WebSocketImpl = WebSocketImpl;
    this.scheduleImpl = scheduleImpl;
    this.attempt = 0;
    this.closedByUser = false;
    this.ws = null;
  }

  connect() {
    this.closedByUser = false;
    this.ws = new this.WebSocketImpl(this.url);

    this.ws.addEventListener('open', () => {
      this.attempt = 0;
      this.logger?.info({ url: this.url }, 'WS terhubung');
      this.onOpen?.();
    });

    this.ws.addEventListener('message', (event) => {
      let data;
      try {
        data = JSON.parse(event.data);
      } catch (err) {
        this.logger?.warn({ url: this.url, err: String(err) }, 'gagal parse pesan WS, dilewati');
        return;
      }
      this.onMessage?.(data);
    });

    this.ws.addEventListener('close', () => {
      this.onClose?.();
      if (!this.closedByUser) this._scheduleReconnect();
    });

    this.ws.addEventListener('error', (event) => {
      const message = event && (event.message || event.error || event);
      this.logger?.warn({ url: this.url, error: String(message) }, 'WS error');
      this.onError?.(event);
    });

    return this;
  }

  _scheduleReconnect() {
    const delay = computeBackoffMs(this.attempt);
    this.attempt += 1;
    this.logger?.warn({ url: this.url, delayMs: delay, attempt: this.attempt }, 'WS terputus, reconnect dijadwalkan');
    this.scheduleImpl(() => this.connect(), delay);
  }

  close() {
    this.closedByUser = true;
    this.ws?.close();
  }
}

module.exports = { buildCombinedStreamUrl, computeBackoffMs, ReconnectingStream };
