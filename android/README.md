# android/

**FASE 10 — Aplikasi Android Mandiri (tanpa Termux).** Proyek Kotlin native,
dibangun Gradle. Sejak 2026-11-07, arsitektur proyek berubah total: logic bot
yang sebelumnya direncanakan di Node.js/Termux (`../backend/`) sekarang
ditulis ulang di sini sebagai satu APK mandiri. Lihat
`../docs/PROJECT_STATUS.md` untuk alasan & detail perubahan ini.

## Status pemantauan

Tombol **Mulai bot** di tab Scanner menjalankan layanan pemantauan BTC/USDT. Layar menampilkan
bid, ask, dan waktu pembaruan dari bursa. Harga terakhir ditandai kedaluwarsa
saat koneksi putus atau data berumur 10 detik. Tombol **Hentikan bot** di tab yang sama menghentikan
layanan. Opsi Auto Start di tab Akun mengikuti pilihan pengguna setelah reboot.

GitHub Actions menjalankan unit test lalu membuat APK debug. Hasil uji di HP
dan daftar fitur berikutnya dicatat di [status proyek](../docs/PROJECT_STATUS.md).

## Build lokal (nanti, kalau sudah ada Android Studio/PC)

```bash
cd android
gradle assembleDebug   # build APK
gradle test             # unit test JUnit (JVM murni, tanpa emulator)
```

(Belum ada `gradlew` wrapper -- sengaja, lihat catatan di
`.github/workflows/android-build.yml`.)

## Dapatkan APK tanpa PC (lewat GitHub Actions)

```bash
gh run list -R <owner>/me-futures-ai-bot
gh run download <run-id> -n me-project-debug-apk -R <owner>/me-futures-ai-bot
```

Lalu install `app-debug.apk` lewat aplikasi Files di HP (izinkan "install
unknown apps" sekali untuk app yang dipakai membukanya).

## Struktur

Kode layanan dan layar utama ada di `app/src/main/java/com/mefuturesaibot/app/`.
Subfolder `exchange/` berisi penandatanganan permintaan, koneksi WebSocket,
dan pembacaan harga. Tes JVM ada di `app/src/test/`.
