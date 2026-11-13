# PROJECT STATUS

**Fase saat ini:** FASE 10 Android -- Foreground Service dibangun + 3 temuan
audit kritis diperbaiki (tipe service, bug reconnect, tombol Start/Stop).
**BELUM dites di device nyata sejak perbaikan ini** -- CI hijau tidak cukup
untuk perubahan Service, wajib device nyata, lihat CHANGELOG.md.
**Terakhir diperbarui:** 2026-11-10

## PERUBAHAN ARSITEKTUR BESAR (2026-11-07)

Atas permintaan eksplisit: proyek berubah dari "backend Node.js di Termux +
APK monitor terpisah" menjadi **satu aplikasi Android Kotlin mandiri**
(tanpa Termux sama sekali untuk menjalankan bot).

- **Pendekatan**: Kotlin murni ditulis ulang, BUKAN membungkus Node.js lama.
- **`backend/` (Node.js, 55 test)** tetap di repo sebagai **referensi desain**, TIDAK dijalankan.

## Keterbatasan Verifikasi -- PENTING

Lingkungan kerja Claude tidak punya Android SDK/Gradle/jaringan. Semua kode
`android/` hanya terverifikasi lewat GitHub Actions (compile+unit test) --
**bukan jaminan runtime benar**, terutama untuk Foreground Service (lihat
Audit Report, batas waktu Android 15+, race condition reconnect, dll). Area
yang menyentuh Service/lifecycle/notifikasi **wajib dites manual di device**.

## Yang Sudah Dibangun & TERBUKTI (compile+test lewat CI)

- Gradle Kotlin DSL: AGP 8.13.0, Kotlin 2.2.21, compileSdk/targetSdk 36, minSdk 26.
- `Signer.kt` -- HMAC-SHA256, 6 unit test.
- `ReconnectingWebSocket.kt` -- auto-reconnect + backoff/jitter + OkHttpClient
  singleton, scheduler bisa diuji (`Scheduler` interface) -- 4 unit test murni
  (backoff) + 2 unit test pola-logika (close/cancel, **bukan test class asli**,
  lihat TODO.md).
- `BotForegroundService.kt` -- Service `START_STICKY`, `foregroundServiceType="specialUse"`
  (bukan `dataSync`, lihat Audit Report #1), notifikasi persisten + `setContentIntent`,
  `ACTION_STOP` untuk berhenti bersih.
- `MainActivity.kt` -- murni UI, tombol Start/Stop eksplisit, polling status Service.
- Total 11 unit test Kotlin, semua lulus di JVM (CI).

## Yang BELUM dibangun sama sekali

REST client Kotlin, symbol registry, candle store, indikator, signal engine,
risk management, order execution, TP/SL, anti-duplicate, penyimpanan API Key,
Boot Receiver, battery optimization exemption otomatis, LIVE MODE lock di
Kotlin, paper trading, jurnal/PnL, dashboard lengkap. Rincian lengkap:
[`AUDIT_REPORT.md`](AUDIT_REPORT.md) dan [`TODO.md`](TODO.md).

## Menunggu dari Anda

- Ikon app kustom (sengaja belum dibuat).
- Konfirmasi nama app final.
- Mode Posisi (One-way/Hedge), pilihan database (Room).

## Yang TIDAK berubah

Aturan trading, risk management, keamanan API Key, fitur rekomendasi yang
sudah disetujui -- semua tetap berlaku, cuma bahasa implementasinya Kotlin.
Temuan API Binance (migrasi Algo Order, split WS /public//market//private)
tetap relevan untuk diimplementasikan nanti.

## Berikutnya

Roadmap F (lihat `AUDIT_REPORT.md`): Boot Receiver, lalu REST client Kotlin
(pakai `Signer.kt` yang sudah teruji), data layer, penyimpanan API Key --
tetap satu-satu, diuji & dites device tiap langkah.
