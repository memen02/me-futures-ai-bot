# ME FUTURES AI BOT

Aplikasi Android **mandiri** (Kotlin, tanpa Termux) untuk trading otomatis
Binance USDⓈ-M Futures — tanpa bergantung pada LLM/API AI apa pun saat live
trading.

> ⚠️ **Peringatan risiko**: trading futures dengan leverage dapat menyebabkan
> kerugian modal yang signifikan. Proyek ini tidak menjanjikan profit atau win
> rate tertentu. Gunakan dengan risiko Anda sendiri, mulai dari mode `paper`
> atau Binance Futures **Testnet**, dan baca `docs/SECURITY.md` sebelum mengisi
> API Key sungguhan.

## Status Proyek

**Pivot arsitektur (2026-11-07)**: dari "backend Node.js/Termux + APK
terpisah" menjadi **satu app Android Kotlin mandiri**. Baru fondasi
(`android/` berisi skeleton Gradle + 1 Activity minimal, BELUM PERNAH
di-compile oleh siapa pun — verifikasi pertama lewat GitHub Actions).
Lihat [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) untuk status
lengkap & alasan perubahan, dan [`docs/PROJECT_MASTER.md`](docs/PROJECT_MASTER.md)
untuk arsitektur/fitur (sebagian besar masih berlaku, cuma bahasa
implementasinya berubah).

## Struktur Proyek

```
android/   App Android Kotlin mandiri (AKTIF dikembangkan)
backend/   Bot Node.js Fase 1-2 (REFERENSI desain, tidak lagi dijalankan)
docs/      Dokumentasi master proyek
```

## Build (lewat GitHub Actions, bukan lokal)

Karena tidak pakai PC/Termux, APK dibangun lewat GitHub Actions:

1. Push repo ini ke GitHub (lihat `docs/PROJECT_STATUS.md` untuk 7 langkah setupnya).
2. Cek tab **Actions** di github.com — workflow **Android Build**.
3. Unduh APK dari hasil build (artifact `me-futures-ai-bot-debug-apk`), install manual di HP.

## Dokumentasi

| File | Isi |
|---|---|
| `docs/PROJECT_STATUS.md` | Status terkini & keterbatasan verifikasi (baca ini dulu) |
| `docs/PROJECT_MASTER.md` | Arsitektur, rencana fase, fitur yang disetujui |
| `docs/CHANGELOG.md` | Riwayat perubahan |
| `docs/TODO.md` | Daftar kerja berikutnya |
| `docs/SECURITY.md` | Aturan keamanan & penyimpanan API Key |
| `docs/API_SPEC.md` | Endpoint Binance yang dipakai (diverifikasi dari dokumentasi resmi) |
| `docs/TEST_PLAN.md` | Strategi pengujian |
| `docs/RESTORE_GUIDE.md` | Panduan pemulihan jika HP/data hilang |

## Lisensi

MIT — lihat [`LICENSE`](LICENSE). Setiap pengguna wajib memakai API Key Binance
miliknya sendiri; API Key pembuat proyek tidak pernah disertakan di source code
atau APK.
