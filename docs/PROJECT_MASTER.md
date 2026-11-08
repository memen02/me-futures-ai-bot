# PROJECT MASTER — ME FUTURES AI BOT

> **PERUBAHAN ARSITEKTUR (2026-11-07)**: proyek ini sekarang jadi **satu aplikasi
> Android Kotlin mandiri** (tanpa Termux untuk menjalankan bot), bukan lagi
> "backend Node.js/Termux + APK monitor terpisah" seperti isi dokumen asli di
> bawah ini. Dokumen ini TETAP berlaku untuk: aturan trading, risk management,
> keamanan, dan fitur-fitur yang direkomendasikan -- SEMUA itu tidak berubah.
> Yang berubah HANYA bahasa/platform implementasi (Node.js -> Kotlin) dan
> penghapusan ketergantungan pada Termux. Detail & alasan lengkap:
> [`PROJECT_STATUS.md`](PROJECT_STATUS.md).

Dokumen arsitektur & rencana pembangunan (bagian "Rencana 12 Fase" di bawah
mencerminkan rencana ASLI sebelum pivot -- status terkini selalu di
PROJECT_STATUS.md). Versi lengkap (dengan narasi
pertimbangan desain) ada di dokumen perencanaan terpisah yang dibagikan saat
sesi arsitektur awal; file ini adalah versi ringkas yang dikomit ke repo.

## Ringkasan

Bot trading otomatis Binance USDⓈ-M Futures, berjalan lokal di Termux
(Node.js, Android, RAM 4GB), tanpa dependensi LLM/API AI saat live trading.
APK Android (Kotlin) untuk monitoring & kontrol. Backup/recovery lewat GitHub.

## Prinsip Arsitektur

- **Lokal & tanpa LLM saat live** — semua keputusan trading dari indikator/
  statistik/aturan, bukan panggilan API AI.
- **Modular monolith**, bukan microservices — satu proses Node.js, hemat RAM.
- **Binance sebagai single source of truth** — rekonsiliasi posisi & order ke
  Binance sebelum aksi apa pun (entry baru, restart, dsb).
- **Default aman = NO TRADE** — status tidak jelas → tidak ada aksi baru.
- **Satu pintu ke exchange** — semua panggilan REST/WS lewat Exchange Gateway.

## Temuan Teknis Kritis (Binance API)

Sejak migrasi efektif 2025-12-09, order kondisional USDⓈ-M Futures
(`STOP`, `STOP_MARKET`, `TAKE_PROFIT`, `TAKE_PROFIT_MARKET`,
`TRAILING_STOP_MARKET`) pindah dari `POST /fapi/v1/order` ke endpoint Algo
Service `POST /fapi/v1/algoOrder` (`algoType=CONDITIONAL`). Endpoint lama
menolak order jenis ini dengan error `-4120 STOP_ORDER_SWITCH_ALGO`. Detail
lengkap: [`API_SPEC.md`](API_SPEC.md). Ini WAJIB diverifikasi ulang dari
dokumentasi resmi sebelum implementasi Fase 6 (dokumentasi API bisa berubah).

## Komponen Sistem

- **Exchange Gateway** — REST+WS client Binance (satu-satunya yang tahu detail endpoint).
- **Data Layer** — symbol registry, candle store (rolling window), cache TTL.
- **Analysis Layer** — indikator, pattern, market structure, microstructure, regime, correlation.
- **Decision Layer** — Strategy Lab, confidence calibrator, news/event filter.
- **Risk & Execution Layer** — Portfolio Risk Engine, Execution Engine, Protection Manager, Reconciliation Engine.
- **Operasional** — Self-Learning/Analytics, Resource Manager, Backup & Recovery Manager, Watchdog.
- **Antarmuka** — Local API Server (localhost-only, token auth) untuk APK Android.

## Struktur Folder

```
me-futures-ai-bot/
├── backend/src/{core,exchange,data,indicators,patterns,structure,
│                microstructure,regime,strategy,risk,execution,
│                analytics,resourceManager,backup,watchdog,api,db}
├── backend/test/
├── backend/scripts/
├── android/
└── docs/
```

## Rencana 12 Fase

1. Project Foundation — **selesai**
2. Binance Futures Data Engine — **selesai**, lihat PROJECT_STATUS.md (menunggu verifikasi jaringan Anda)
3. Market Scanner & Indikator
4. Strategy Engine & Market Intelligence
5. Risk Management & Portfolio Protection
6. Order Execution, TP/SL (via Algo Order), Anti-Duplicate
7. Self Learning & Performance Analytics
8. Smart Resource Manager
9. GitHub Backup & Recovery
10. Android Monitor APK
11. Watchdog & Auto Recovery
12. Testing, Security Audit, Release

## Keputusan Default (dipakai sampai ada instruksi lain)

- Mode Posisi Binance: **One-way Mode** (lebih sederhana; bisa diganti ke Hedge Mode sebelum Fase 6 jika diminta).
- Database: `better-sqlite3` sebagai target utama, fallback `sql.js` (WASM) jika gagal compile native di Termux — diuji di Fase 2.
- Mode awal: `BOT_MODE=paper` (simulasi lokal) sebagai default, naik ke `testnet` saat Fase 2+ siap diuji sungguhan. `live` terkunci secara teknis (bukan cuma dokumentasi) sampai Fase 12 lulus.
- Repo: `me-futures-ai-bot`, disarankan privat sampai Fase 12 selesai.

Lihat [`PROJECT_STATUS.md`](PROJECT_STATUS.md) untuk status konfirmasi terkini.

## Fitur Tambahan Direkomendasikan (dikonfirmasi 2026-11-07)

Di luar fitur wajib yang sudah ada di spesifikasi awal, berikut fitur yang
direkomendasikan dan DISETUJUI untuk ditambahkan -- dipasang ke fase yang
sudah ada (tidak menambah fase baru), dibangun saat fase terkait tiba.

### Analisis & Sinyal (Fase 3-4)
- **Multi-timeframe confluence scoring** -- sinyal valid hanya kalau beberapa timeframe searah (mis. 15m+1h+4h), bukan dari satu timeframe saja.
- **Ambang confidence adaptif terhadap volatilitas/regime** -- bukan angka tetap 70% di semua kondisi pasar.
- **Filter sesi waktu** -- hindari entry di jam likuiditas rendah (celah antar sesi Asia/Eropa/US).
- **Correlation-aware position filtering** -- LONG BTC+ETH+SOL bersamaan dihitung sebagai 1 eksposur beta, bukan 3 posisi independen (terhubung ke Correlation Guard di Fase 5).

### Eksekusi & Manajemen Posisi (Fase 5-6)
- **Partial take-profit (scale-out)** -- TP bertahap (mis. 50% di R:R 1:2, sisanya trailing) bukan TP sekali penuh.
- **Breakeven stop-loss** -- SL otomatis digeser ke entry setelah profit mencapai threshold tertentu.
- **Spread/slippage guard sebelum entry** -- cek `bookTicker` (sudah ditangkap Fase 2) sebelum kirim order, skip kalau spread tidak wajar meski sinyal teknikal valid.

### Observability & Keamanan Backend (Fase 7, 10, 12)
- **Jurnal NO_TRADE** -- catat juga alasan setiap kali bot MEMILIH tidak entry, bukan cuma trade yang jadi.
- **Notifikasi anomali** -- push alert kalau WS putus lama, funding rate ekstrem, atau liquidation cascade besar.
- **Cek izin API Key saat startup** -- validasi API Key yang dipakai TIDAK punya izin withdraw.
- **Emergency Stop via flag file** -- bisa stop cepat dari APK tanpa menunggu proses backend mati total/restart.

### Android App -- Keamanan & Kontrol (Fase 10)
- **Kunci biometrik untuk aksi sensitif** -- ganti API Key, ubah pengaturan risiko, Emergency Stop minta sidik jari/Face Unlock.
- **Dua level stop terpisah**: Stop normal (hentikan entry baru, posisi tetap diproteksi -- sudah di spesifikasi awal) vs **Panic Mode** (tutup SEMUA posisi + stop total).
- **Pairing APK↔backend lewat QR code** -- scan dari layar Termux, bukan ketik token manual (lebih kecil risiko salah ketik).
- **Konfirmasi dua langkah** untuk aksi berisiko (Emergency Stop, ganti API Key).

### Android App -- Observability (Fase 10)
- **Status koneksi granular**: REST OK? / WS market OK? / WS user-data OK? / backend reachable? -- dipisah, bukan cuma "Connected/Disconnected".
- **Layar ringkasan "sehat/tidak"** di halaman pertama app (bot jalan?, koneksi aman?, RAM/storage aman?).
- **Grafik equity curve** (PnL kumulatif).
- **Notifikasi berjenjang**: info biasa vs kritis (kritis bisa override mode senyap HP).

### Android App -- Kenyamanan (Fase 10)
- **Widget layar utama**: status bot + PnL hari ini + jumlah posisi terbuka.
- **Filter & pencarian riwayat trade** (per simbol/strategi/tanggal).
- **Export laporan (CSV)**.

Prioritas tertinggi di antara semua ini (paling terkait keamanan & troubleshooting): kunci biometrik, pairing QR code, dan status koneksi granular.
