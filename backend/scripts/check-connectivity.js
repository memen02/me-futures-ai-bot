#!/usr/bin/env node
'use strict';

/**
 * Jalankan di Termux (ada akses internet) untuk memverifikasi asumsi base URL
 * REST & WS yang dicatat di src/exchange/endpoints.js, SEBELUM Fase 3 mulai
 * dibangun di atasnya. Lingkungan kerja Claude tidak punya akses jaringan,
 * jadi script ini belum pernah benar-benar dijalankan -- jalankan ini dulu,
 * lalu salin hasil [OK]/[GAGAL]-nya kembali ke percakapan supaya
 * src/exchange/endpoints.js bisa dikunci final (atau diperbaiki kalau salah).
 *
 * Pemakaian:
 *   node backend/scripts/check-connectivity.js             # cek REST+WS testnet (aman, default)
 *   node backend/scripts/check-connectivity.js --mainnet    # cek REST+WS mainnet, HANYA baca data publik (TIDAK membuka posisi apa pun)
 */

const { REST, WS, REST_PATHS, restBaseUrl } = require('../src/exchange/endpoints');

const isMainnet = process.argv.includes('--mainnet');

function log(label, ok, extra = '') {
  const mark = ok ? 'OK   ' : 'GAGAL';
  console.log(`[${mark}] ${label}${extra ? ' - ' + extra : ''}`);
}

async function checkRest(restBase) {
  try {
    const res = await fetch(`${restBase}${REST_PATHS.ping}`);
    log(`REST ping (${restBase})`, res.ok, `status ${res.status}`);
  } catch (err) {
    log(`REST ping (${restBase})`, false, String(err));
  }

  try {
    const res = await fetch(`${restBase}${REST_PATHS.serverTime}`);
    const body = await res.json();
    const skewMs = Date.now() - body.serverTime;
    const skewOk = Math.abs(skewMs) < 5000;
    log(`REST serverTime (${restBase})`, res.ok, `selisih jam lokal vs server: ${skewMs}ms${skewOk ? '' : ' -- TERLALU BESAR, cek jam HP Anda!'}`);
  } catch (err) {
    log(`REST serverTime (${restBase})`, false, String(err));
  }

  try {
    const res = await fetch(`${restBase}${REST_PATHS.exchangeInfo}`);
    const body = await res.json();
    const count = Array.isArray(body.symbols) ? body.symbols.length : 0;
    log(`REST exchangeInfo (${restBase})`, res.ok && count > 0, `${count} simbol ditemukan`);
  } catch (err) {
    log(`REST exchangeInfo (${restBase})`, false, String(err));
  }
}

function checkWs(label, baseUrl, timeoutMs = 8000) {
  return new Promise((resolve) => {
    if (typeof WebSocket === 'undefined') {
      log(`WS ${label}`, false, 'global WebSocket tidak tersedia di Node ini -- butuh Node >=22, cek `node -v`');
      resolve(false);
      return;
    }
    const streamUrl = `${baseUrl}/stream?streams=btcusdt@bookTicker`;
    let settled = false;
    let ws;
    try {
      ws = new WebSocket(streamUrl);
    } catch (err) {
      log(`WS ${label} (${streamUrl})`, false, String(err));
      resolve(false);
      return;
    }

    const timer = setTimeout(() => {
      if (!settled) {
        settled = true;
        log(`WS ${label} (${streamUrl})`, false, `timeout ${timeoutMs}ms, tidak ada data masuk`);
        try { ws.close(); } catch { /* abaikan */ }
        resolve(false);
      }
    }, timeoutMs);

    ws.addEventListener('message', () => {
      if (!settled) {
        settled = true;
        clearTimeout(timer);
        log(`WS ${label} (${streamUrl})`, true, 'data diterima');
        ws.close();
        resolve(true);
      }
    });

    ws.addEventListener('error', (event) => {
      if (!settled) {
        settled = true;
        clearTimeout(timer);
        log(`WS ${label} (${streamUrl})`, false, String(event.message || event));
        resolve(false);
      }
    });
  });
}

async function main() {
  console.log(`== ME FUTURES AI BOT - Connectivity Check (${isMainnet ? 'MAINNET read-only' : 'TESTNET'}) ==`);
  console.log(`Node version: ${process.version}\n`);

  const restBase = isMainnet ? REST.mainnet : restBaseUrl('testnet');
  await checkRest(restBase);

  console.log('');
  if (isMainnet) {
    await checkWs('mainnet /public (bookTicker)', WS.mainnet.public);
  } else {
    await checkWs('testnet split /public (bookTicker)', WS.testnetSplit.public);
    await checkWs('testnet legacy gabungan (bookTicker)', WS.testnetLegacy.all);
  }

  console.log('\nSelesai. Salin baris [OK]/[GAGAL] di atas ke percakapan supaya endpoints.js bisa dikunci final.');
}

main().catch((err) => {
  console.error('Script gagal total:', err);
  process.exit(1);
});
