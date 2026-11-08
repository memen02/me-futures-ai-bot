# Changelog

Format mengacu longgar pada [Keep a Changelog](https://keepachangelog.com/);
versi mengikuti [SemVer](https://semver.org/) selama masih 0.x (struktur bisa berubah).

## [0.1.0] - 2026-11-07

### Added — FASE 1: Project Foundation
- Struktur folder `backend/`, `android/`, `docs/` sesuai PROJECT_MASTER.md.
- `package.json` (CommonJS, Node >=18), dependency eksternal sengaja kosong di fase ini.
- Modul inti tanpa dependency: `src/core/config`, `src/core/logger`, `src/core/events`.
- `BOT_MODE` (paper/testnet/live) dengan live diblokir di level kode.
- Entry point `src/index.js` (startup banner, heartbeat, graceful shutdown, mode `--selftest`).
- Unit test `node --test` (7 test, semua lulus).
- Dokumentasi dasar: README, PROJECT_MASTER, PROJECT_STATUS, TODO, SECURITY,
  API_SPEC (termasuk temuan migrasi Algo Order Binance), TEST_PLAN,
  INSTALLATION, RESTORE_GUIDE (stub).
- `.env.example`, `.gitignore`, `LICENSE` (MIT), CI stub (`backend-ci.yml`),
  `scripts/install.sh`.

## [0.2.0] - 2026-11-07

### Added — FASE 2: Binance Futures Data Engine
- `src/exchange/endpoints.js` — registry terpusat base URL REST/WS & path endpoint, termasuk catatan migrasi WS `/public`/`/market`/`/private` dan migrasi Algo Order (TP/SL).
- `src/exchange/signer.js` — HMAC-SHA256 signing & query string builder (murni, diuji terhadap test vector independen).
- `src/exchange/restClient.js` — REST client signed/public pakai `fetch` bawaan Node, retry/backoff untuk status transient, timeout via `AbortController`.
- `src/exchange/wsClient.js` — WS client pakai `WebSocket` bawaan Node (TANPA dependency `ws`), auto-reconnect dengan backoff+jitter.
- `src/data/symbolRegistry.js` — parsing `exchangeInfo`, filter simbol PERPETUAL+TRADING, deteksi kontrak baru, validasi order terhadap filter (price/qty/notional) — termasuk koreksi field Futures `notional` (bukan `minNotional`).
- `src/data/candleStore.js` — rolling window candle per simbol+timeframe (hemat RAM), hanya menyimpan candle closed, backfill dari REST klines.
- `src/data/marketSnapshot.js` — Tier-1 store ringan (ticker/markPrice/likuidasi semua simbol), TTL freshness.
- `scripts/check-connectivity.js` — alat verifikasi koneksi REST+WS untuk dijalankan di Termux (lingkungan Claude tanpa akses jaringan).
- 48 unit test baru (total 55), semua lulus tanpa jaringan (logic murni + mock fetch/WebSocket).
- `package.json`: `engines.node` dinaikkan ke `>=22.0.0` (untuk `WebSocket`/`fetch` bawaan), masih 0 dependency eksternal.

### Changed
- Dokumentasi `API_SPEC.md`, `TODO.md`, `PROJECT_STATUS.md` diperbarui dengan temuan verifikasi dokumentasi resmi Binance per 2026-11-07.

### Belum diverifikasi dengan koneksi nyata (lihat TODO.md)
- Base URL REST/WS testnet — perlu dikonfirmasi lewat `npm run check-connectivity` di Termux.

## [0.3.0] - 2026-11-07

### Changed — PIVOT ARSITEKTUR BESAR
- Proyek berubah dari "backend Node.js/Termux + APK monitor terpisah" menjadi
  **satu aplikasi Android Kotlin mandiri** (tanpa Termux untuk menjalankan bot).
- `backend/` (Fase 1-2, Node.js, 55 test) status berubah jadi **referensi**, bukan
  kode yang dijalankan lagi -- tetap ada di repo, tidak dihapus.
- Pendekatan `nodejs-mobile` (membungkus Node.js ke APK) dipertimbangkan dan
  ditolak karena kompleksitas NDK/JNI tambahan.

### Added — Fondasi android/ (Fase 10, dipercepat)
- Struktur proyek Gradle Kotlin DSL (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`).
- `MainActivity.kt` minimal (layout programatik, nol dependency eksternal).
- Icon placeholder (vector drawable).
- `.github/workflows/android-build.yml` -- build APK debug via `gradle/actions/setup-gradle` (tanpa `./gradlew`, karena `gradle-wrapper.jar` tidak bisa dibuat di lingkungan Claude).

### Keterbatasan penting
- Kode `android/` **belum pernah di-compile oleh siapa pun** -- lingkungan kerja
  Claude tidak punya Android SDK/Gradle/jaringan. Verifikasi pertama terjadi
  lewat GitHub Actions setelah push. Lihat PROJECT_STATUS.md.
