# android/

**FASE 10 — Aplikasi Android Mandiri (tanpa Termux).** Proyek Kotlin native,
dibangun Gradle. Sejak 2026-11-07, arsitektur proyek berubah total: logic bot
yang sebelumnya direncanakan di Node.js/Termux (`../backend/`) sekarang
ditulis ulang di sini sebagai satu APK mandiri. Lihat
`../docs/PROJECT_STATUS.md` untuk alasan & detail perubahan ini.

## Status pemantauan

Pasar memindai kontrak USDⓈ-M perpetual melalui layanan Android dengan notifikasi tersendiri. Tab Bot memuat kontrol eksekusi, Auto Start setelah reboot, tren SMA 20 pada 1m dan 5m serta trailing untuk posisi Paper baru. Android dapat menghentikan layanan ketika pengguna melakukan force stop.

Trading memuat posisi aktif dan hasil posisi tertutup. Pilih Paper, Demo, atau Mainnet untuk melihat hasil kalender UTC yang tercatat oleh aplikasi. Akumulasi bulan tetap disimpan setelah rincian keluar dari daftar 150 penutupan terakhir. Jurnal Paper lama dapat dipulihkan sejauh datanya masih tersimpan.

Tiket manual Paper memakai kuotasi publik, SL, TP, biaya, slippage, batas risiko, dan batas satu posisi. Eksekutor bursa memakai BTCUSDT. Posisi manual yang dibuat langsung di Binance berada di luar jurnal bot.

Ikon offline dipetakan ke aset dasar kontrak. Snapshot 24 November 2026 memuat 567 kontrak dan 522 aset dasar. Sumber gambar mencakup katalog aset Binance, Binance Alpha dan pemetaan pasangan perpetual Binance di CoinMarketCap. Lembar gambar dimuat sesuai kebutuhan.

## Build lokal (nanti, kalau sudah ada Android Studio/PC)

```bash
cd android
gradle assembleDebug   # build APK
gradle test             # unit test JUnit (JVM murni, tanpa emulator)
```

(Belum ada `gradlew` wrapper -- sengaja, lihat catatan di
`.github/workflows/android-build.yml`.)

## APK untuk HP

GitHub Actions menjalankan unit test dan membangun varian `real` untuk satu aplikasi Demo/Mainnet. Artefak `me-project-unified-unsigned` memerlukan penandatanganan dengan sertifikat aplikasi sebelum instalasi sebagai pembaruan.

## Struktur

Kode layanan dan layar utama ada di `app/src/main/java/com/mefuturesaibot/app/`.
Subfolder `exchange/` berisi penandatanganan permintaan, koneksi WebSocket,
dan pembacaan harga. Tes JVM ada di `app/src/test/`.
