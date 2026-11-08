# RESTORE_GUIDE.md

Status: **stub** — akan diisi lengkap di Fase 9 (GitHub Backup & Recovery).
Alur tingkat tinggi (sesuai rencana master) sudah dicatat di sini supaya
tidak hilang:

1. Unduh source code dari GitHub.
2. Install Termux & dependensi (`docs/INSTALLATION.md`).
3. Jalankan recovery installer (menyusul Fase 9).
4. Masukkan kembali API Key pengguna (lewat APK, Fase 10).
5. Pulihkan database dari backup terenkripsi jika tersedia (Fase 9).
6. Periksa konfigurasi (`.env`).
7. Hubungkan ke Binance Futures.
8. Sinkronisasi (reconciliation) posisi, pending order, dan TP/SL langsung
   dengan Binance — **jangan pernah** menganggap backup lokal sebagai status
   posisi Binance yang paling baru.
9. Jalankan mode aman (`BOT_MODE=paper` atau `testnet`) terlebih dahulu.
10. Izinkan trading kembali hanya setelah seluruh pemeriksaan berhasil.
