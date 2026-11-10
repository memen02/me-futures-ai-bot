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

## [0.3.1] - 2026-11-08

### Verified
- **Build pertama di GitHub Actions BERHASIL** (run #42462301338, job selesai dalam 1m59s).
  Ini verifikasi riil pertama kode `android/` -- sebelumnya belum pernah di-compile sama sekali.
  Repo: `memen02/me-futures-ai-bot` (privat).
- `Backend CI` (test Node.js referensi, 55 test) juga tetap lulus di GitHub Actions.
- Peringatan non-blocking di log Actions (tidak perlu aksi segera): `actions/setup-java@v4`
  deprecated (migrasi ke v5 disarankan nanti), runner Node.js 20 dipaksa jalan di Node 24
  oleh GitHub, `ubuntu-latest` akan migrasi ke Ubuntu 26 per 2026-10-19.

## [0.3.2] - 2026-11-08

### Verified
- **APK terpasang & terbuka di perangkat nyata (Redmi Note 7, crDroid) tanpa crash** --
  layar fondasi tampil persis seperti yang dirancang. Ini menutup "step 0" sepenuhnya:
  compile (GitHub Actions) -> install -> launch, semua terverifikasi end-to-end.

## [0.4.0] - 2026-11-08

### Added — FASE 10 Increment 2: Auto-Reconnect
- `Signer.kt` -- HMAC-SHA256 + query string builder, murni Kotlin/JVM, porting dari
  referensi Node.js `signer.js`. 6 JUnit test (termasuk test vector independen yang
  sama dipakai di versi Node.js).
- `ReconnectingWebSocket.kt` -- wrapper OkHttp WebSocket dengan auto-reconnect
  (backoff eksponensial + jitter). Callback selalu dipanggil di main thread. 3 JUnit
  test untuk fungsi backoff murni (`computeBackoffMs`).
- `MainActivity.kt` -- didemokan LANGSUNG di layar: konek ke stream publik Binance
  (`btcusdt@bookTicker`, kategori `/public`), status berubah live
  (Menyambungkan/Tersambung/Terputus/Menyambung ulang). Bisa diuji manual dengan
  toggle mode pesawat.
- Dependency baru: `com.squareup.okhttp3:okhttp:5.4.0` (WebSocket -- platform Android
  minSdk 26 tidak punya WebSocket bawaan, baru ada mulai API 34), `junit:junit:4.13.2` (testImplementation).
- `AndroidManifest.xml`: tambah `<uses-permission android:name="android.permission.INTERNET" />`.
- `.github/workflows/android-build.yml`: tambah step `gradle test` sebelum `assembleDebug`.

### Keterbatasan yang masih berlaku
- Reconnect baru jalan SELAMA APP TERBUKA (lifecycle onStart/onStop) -- belum
  persisten di background. Itu scope increment BERIKUTNYA (Foreground Service +
  BOOT_COMPLETED receiver).
- Belum pernah dites di GitHub Actions/device sungguhan -- menunggu hasil push berikutnya.

## [0.4.1] - 2026-11-08

### Fixed
- **Bug build nyata ditemukan & diperbaiki**: `okhttp-android:5.4.0` mewajibkan
  `compileSdk >= 36`, proyek masih `compileSdk 35`. Terbukti dari log kegagalan
  GitHub Actions run **42528412688** (`CheckAarMetadataWorkAction`), bukan dugaan.
  Diperbaiki: `compileSdk`/`targetSdk` 35 -> 36 di `android/app/build.gradle.kts`.
- Ditemukan juga: commit sebelumnya (fase10 signer+reconnect) sempat TIDAK ter-push
  ke GitHub sama sekali selama beberapa putaran (tersimpan di folder lepas tanpa git),
  sehingga klaim "Added" di entri 0.4.0 belum pernah terbukti sampai disinkronkan
  ulang pada commit b4e2167.

## [0.4.2] - 2026-11-08

### Verified (bukti nyata, bukan klaim)
- **GitHub Actions run 42554105921: SUKSES PENUH.** `gradle test` (9 JUnit test:
  6 Signer + 3 BackoffCalculator) lulus, `gradle assembleDebug` berhasil, APK
  ter-upload sebagai artifact. Ini pertama kalinya kode Fase 10 Increment 2
  (Signer + ReconnectingWebSocket) benar-benar terbukti compile, setelah 2 bug
  nyata ditemukan & diperbaiki (compileSdk 35->36, Kotlin plugin 2.0.21->2.2.21).
- Catatan: APK hasil build ini BELUM diinstal ulang/dites di device sejak
  perubahan terakhir -- compile sukses, runtime belum diverifikasi ulang.

## [0.5.0] - 2026-11-08

### Fixed — 6 dari 8 temuan Audit Report (dokumentasi #1-4 + android/README + singleton)
- `docs/INSTALLATION.md`, `docs/RESTORE_GUIDE.md` — ditulis ulang total, tidak lagi menyebut Termux sebagai langkah wajib menjalankan bot (Termux kini hanya alat dev opsional untuk push git).
- `docs/SECURITY.md` — ditulis ulang untuk arsitektur Android mandiri; status implementasi dijelaskan jujur (belum ada kode penyimpanan credential), referensi Node.js lama dipindah ke bagian terpisah di bawah.
- `docs/TEST_PLAN.md` — tambah bagian "Fase 10 — Hasil Uji (Android Mandiri)" dengan bukti run 42554105921, termasuk daftar eksplisit apa yang BELUM diuji.
- `android/README.md` — status & diagram folder diperbarui ke Increment 2.
- `android/.../exchange/ReconnectingWebSocket.kt` — `OkHttpClient` dijadikan singleton (`sharedHttpClient`, lazy) menggantikan `OkHttpClient()` baru tiap instance (temuan audit #5).
- `OkHttpSingletonTest.kt` (baru) — `assertSame` membuktikan singleton benar-benar satu instance, bukan klaim di komentar. Total test Android: 10 (6 Signer + 3 Backoff + 1 Singleton).

### Belum dikerjakan (sengaja, sesuai aturan "jangan ubah banyak modul sekaligus")
- MainActivity.kt & AndroidManifest.xml (temuan #6-7 di tabel D) — ini butuh Foreground Service yang belum dibuat (Roadmap F langkah 3-4), bukan sekadar "fix", jadi dikerjakan terpisah.
