# ME Project

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
terpisah" menjadi **satu app Android Kotlin mandiri**. Aplikasi sudah memiliki layanan pemantauan BTC/USDT,
tombol Start/Stop, dan opsi mulai otomatis setelah reboot. GitHub Actions
menjalankan unit test dan membuat satu APK ME Project. Instalasi baru dimulai
dalam mode Demo. Di **Pengaturan → Mode trading**, pilih Demo untuk saldo virtual
atau Mainnet untuk dana asli. Key, order bot, dan hasil transaksi disimpan per mode.
Mainnet memerlukan pemeriksaan akun, alokasi USDT, dan persetujuan di perangkat.
Untuk berpindah, matikan trading aktif dan tunggu rekonsiliasi posisi serta order bot.
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

Workflow Android Build menghasilkan satu artefak `me-project-unified-unsigned`.
Tandatangani APK dengan kunci ME Project sebelum memasang atau mendistribusikannya.
Paket `com.mefuturesaibot.app.real` memperbarui ME Project Real yang memakai kunci
yang sama. Pada pembaruan, eksekusi Mainnet aktif atau posisi yang sedang diproses
tetap ditangani di Mainnet. Android menyimpan data dari APK Demo lama di paketnya sendiri.

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
