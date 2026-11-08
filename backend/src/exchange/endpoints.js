'use strict';

/**
 * Registry terpusat endpoint & base URL Binance USDⓈ-M Futures.
 *
 * Semua entri di sini diverifikasi dari dokumentasi resmi
 * developers.binance.com/docs/derivatives/usds-margined-futures pada 2026-11-07.
 * Detail & sumber lengkap ada di docs/API_SPEC.md. JANGAN tambah endpoint baru
 * di modul lain tanpa lewat file ini -- supaya kalau ada yang perlu dikoreksi,
 * cukup di satu tempat.
 *
 * PENTING - baca sebelum mengubah / sebelum Fase 3+ dibangun di atas ini:
 *
 * 1. WS mainnet WAJIB pakai struktur baru /public, /market, /private (migrasi
 *    wajib sebelum 2026-04-23 -- berarti SUDAH LEWAT per hari ini). URL lama
 *    (wss://fstream.binance.com/ws atau /stream) TIDAK error, tapi hanya akan
 *    terus mengirim data kategori /public -- kategori /market dan /private
 *    diam tanpa pemberitahuan. Ini sebabnya kode di bawah sudah pakai struktur
 *    baru, bukan yang lama.
 *
 * 2. REST testnet: dokumentasi resmi yang saya temukan konsisten menyebut
 *    "https://demo-fapi.binance.com" sebagai base URL REST testnet TERBARU
 *    (menggantikan domain lama "testnet.binancefuture.com" yang masih muncul
 *    di sebagian cache dokumentasi).
 *
 * 3. WS testnet: dokumentasi yang saya temukan TIDAK konsisten -- ada yang
 *    bilang testnet masih satu base URL gabungan (wss://fstream.binancefuture.com),
 *    belum jelas apakah testnet sudah ikut dipecah /public /market /private
 *    seperti mainnet. Lingkungan kerja saya tidak punya akses jaringan untuk
 *    mengecek langsung.
 *
 * ==> JALANKAN `node backend/scripts/check-connectivity.js` DI TERMUX ANDA
 *     (ada akses internet) sebelum Fase 3 dimulai, untuk mengonfirmasi mana
 *     dari asumsi #2 dan #3 yang benar-benar menerima data. Laporkan hasilnya
 *     ke saya supaya file ini dikunci final.
 */

const REST = Object.freeze({
  mainnet: 'https://fapi.binance.com',
  testnet: 'https://demo-fapi.binance.com',
});

const WS = Object.freeze({
  mainnet: Object.freeze({
    public: 'wss://fstream.binance.com/public',
    market: 'wss://fstream.binance.com/market',
    private: 'wss://fstream.binance.com/private',
  }),
  // Belum terverifikasi dengan koneksi nyata -- lihat catatan #3 di atas.
  testnetSplit: Object.freeze({
    public: 'wss://fstream.binancefuture.com/public',
    market: 'wss://fstream.binancefuture.com/market',
    private: 'wss://fstream.binancefuture.com/private',
  }),
  testnetLegacy: Object.freeze({
    all: 'wss://fstream.binancefuture.com',
  }),
});

const REST_PATHS = Object.freeze({
  ping: '/fapi/v1/ping',
  serverTime: '/fapi/v1/time',
  exchangeInfo: '/fapi/v1/exchangeInfo',
  klines: '/fapi/v1/klines',
  // HANYA untuk entry LIMIT/MARKET biasa -- BUKAN untuk order kondisional. Lihat baris di bawah.
  newOrder: '/fapi/v1/order',
  // WAJIB dipakai untuk STOP_MARKET/TAKE_PROFIT_MARKET/dst sejak migrasi 2025-12-09
  // (endpoint lama menolak dengan error -4120 STOP_ORDER_SWITCH_ALGO). Lihat Fase 6 & API_SPEC.md.
  algoOrder: '/fapi/v1/algoOrder',
  openAlgoOrders: '/fapi/v1/openAlgoOrders',
  allAlgoOrders: '/fapi/v1/allAlgoOrders',
});

// Kategori stream sesuai pembagian resmi /public vs /market (lihat docs/API_SPEC.md).
const WS_STREAM_CATEGORY = Object.freeze({
  bookTicker: 'public',
  depth: 'public',
  aggTrade: 'market',
  markPrice: 'market',
  kline: 'market',
  continuousKline: 'market',
  miniTicker: 'market',
  ticker: 'market',
  forceOrder: 'market',
  compositeIndex: 'market',
  contractInfo: 'market',
  assetIndex: 'market',
});

/** @param {'paper'|'testnet'|'live'} botMode */
function restBaseUrl(botMode) {
  return botMode === 'testnet' ? REST.testnet : REST.mainnet;
}

module.exports = { REST, WS, REST_PATHS, WS_STREAM_CATEGORY, restBaseUrl };
