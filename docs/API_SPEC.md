# API_SPEC.md

Semua endpoint/stream di sini diverifikasi dari dokumentasi resmi
https://developers.binance.com/docs/derivatives/usds-margined-futures pada
**2026-11-07**. Implementasi ada di `backend/src/exchange/endpoints.js` --
file itu adalah satu-satunya sumber kebenaran di kode; dokumen ini adalah
catatan/alasannya.

## REST Base URL

| Mode | Base URL | Status verifikasi |
|---|---|---|
| Mainnet | `https://fapi.binance.com` | Stabil, dikonfirmasi berulang kali dari dokumentasi resmi |
| Testnet | `https://demo-fapi.binance.com` | Dikonfirmasi dari halaman general-info resmi terbaru. **CATATAN**: ada domain lama `testnet.binancefuture.com` yang masih muncul di sebagian cache dokumentasi -- kemungkinan domain lama yang sedang/sudah digantikan. **Belum dites dengan koneksi nyata** (lingkungan Claude tanpa akses jaringan) -- jalankan `npm run check-connectivity` di Termux untuk konfirmasi final. |

## WebSocket Base URL -- PERUBAHAN PENTING

Sejak migrasi **wajib sebelum 2026-04-23** (sudah lewat per hari ini),
Binance memecah base URL WS mainnet jadi 3 kategori:

| Kategori | Base URL | Isi |
|---|---|---|
| Public | `wss://fstream.binance.com/public` | bookTicker, depth (order book) |
| Market | `wss://fstream.binance.com/market` | aggTrade, markPrice, kline, ticker, forceOrder (likuidasi), contractInfo, dll |
| Private | `wss://fstream.binance.com/private` | user data stream (listenKey) -- dipakai mulai Fase 6 |

**Perilaku setelah migrasi**: URL lama (`wss://fstream.binance.com/ws` atau
`/stream`) **tidak error**, tapi hanya akan terus mengirim data kategori
`/public`. Kategori `/market` dan `/private` **diam tanpa pemberitahuan apa
pun** jika masih memakai URL lama. Karena itu `wsClient.js` & `endpoints.js`
sudah langsung pakai struktur baru.

**Testnet**: dokumentasi yang saya temukan TIDAK konsisten -- ada yang
menyebut testnet masih satu base URL gabungan (`wss://fstream.binancefuture.com`),
belum jelas apakah sudah ikut dipecah seperti mainnet. `endpoints.js`
menyediakan KEDUANYA (`testnetSplit` & `testnetLegacy`) dan
`check-connectivity.js` mencoba keduanya untuk melaporkan mana yang benar-
benar menerima data.

## Migrasi Algo Order (TP/SL) -- untuk Fase 6

Sejak migrasi efektif **2025-12-09**, order kondisional USDⓈ-M Futures --
`STOP`, `STOP_MARKET`, `TAKE_PROFIT`, `TAKE_PROFIT_MARKET`,
`TRAILING_STOP_MARKET` -- **tidak lagi** bisa dibuat lewat `POST /fapi/v1/order`.
Endpoint tersebut menolak order jenis ini dengan error `-4120 STOP_ORDER_SWITCH_ALGO`.

Order kondisional wajib lewat **Algo Service**:
- `POST /fapi/v1/algoOrder` (`algoType=CONDITIONAL`) -- tetap mendukung `closePosition` dan `reduceOnly`.
- `GET /fapi/v1/algoOrder`, `/openAlgoOrders`, `/allAlgoOrders` -- verifikasi status.
- `DELETE /fapi/v1/algoOrder` -- membatalkan.

Order entry biasa (`LIMIT`/`MARKET`) tetap lewat `POST /fapi/v1/order`.

## Filter Simbol (`GET /fapi/v1/exchangeInfo`) -- untuk Fase 2/5/6

**Penting**: filter `MIN_NOTIONAL` di Futures memakai field **`notional`**,
BUKAN `minNotional` seperti di Spot. Sudah ditangani dengan benar di
`src/data/symbolRegistry.js` -- dicatat di sini supaya tidak ada yang
"memperbaikinya" jadi salah lagi di kemudian hari.

| Filter | Field dipakai | Field TIDAK dipakai |
|---|---|---|
| PRICE_FILTER | `minPrice`, `maxPrice`, `tickSize` | - |
| LOT_SIZE | `minQty`, `maxQty`, `stepSize` | - |
| MIN_NOTIONAL | **`notional`** | ~~`minNotional`~~ (itu konvensi Spot) |

## WebSocket Streams yang Dipakai Fase 2

| Stream | Kategori | Dipakai untuk |
|---|---|---|
| `<symbol>@kline_<interval>` | market | Candle per simbol (Tier 2, Fase 3+) |
| `!markPrice@arr@1s` | market | Mark price semua simbol sekaligus (Tier 1) |
| `!ticker@arr` | market | Ticker 24 jam semua simbol sekaligus (Tier 1) |
| `!forceOrder@arr` | market | Likuidasi semua simbol sekaligus (Tier 1, untuk Liquidation Trap Detector Fase 4) |
| `!contractInfo` | market | Deteksi kontrak baru real-time (lebih baik dari polling `exchangeInfo` berkala) |
| `<symbol>@bookTicker` | public | Best bid/ask (dipakai `check-connectivity.js` sebagai tes ringan) |

## Belum Diisi (menyusul per fase)

- Fase 3: detail `userTrades`, pemakaian `!contractInfo` di kode (saat ini baru terdaftar di `endpoints.js`, pemakaiannya menyusul).
- Fase 6: detail penuh parameter New Order, New Algo Order, idempotency (`newClientOrderId`), kode error yang ditangani.
- Fase 6: user data stream (`/private`, listenKey).
- Fase 7: income history (fills/komisi/funding sebagai sumber kebenaran laporan PnL).
