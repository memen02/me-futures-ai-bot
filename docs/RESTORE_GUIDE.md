# RESTORE_GUIDE.md — Pemulihan jika HP/data hilang

> Ditulis ulang 2026-11-08. Versi lama mengasumsikan backend Node.js di
> Termux -- itu TIDAK BERLAKU lagi sejak pivot ke aplikasi Android mandiri.

## Jika HP hilang/rusak/ganti HP baru

1. **Kode & riwayat pengembangan**: aman di GitHub (`github.com/<owner>/me-futures-ai-bot`),
   tidak hilang walau HP hilang -- ini justru alasan utama pakai GitHub sejak awal.
2. Di HP baru, unduh APK terbaru lewat GitHub Actions (lihat
   [`INSTALLATION.md`](INSTALLATION.md) bagian 1) dan install.
3. **Data di dalam app** (kalau sudah ada API Key tersimpan, riwayat trade, dsb --
   fitur ini BELUM ada per 2026-11-08, menyusul di roadmap) akan hilang bersama
   HP lama kecuali sudah ada mekanisme backup data terenkripsi. Status fitur ini:
   lihat [`AUDIT_REPORT.md`](AUDIT_REPORT.md) dan [`TODO.md`](TODO.md).
4. **API Key Binance**: tidak pernah tersimpan di GitHub (lihat
   [`SECURITY.md`](SECURITY.md)) -- harus dimasukkan ulang lewat aplikasi setelah
   install di HP baru, begitu fitur input API Key sudah ada.
5. **Jangan pernah menganggap posisi/order yang tercatat secara lokal sebagai
   status terbaru** -- begitu fitur trading aktif nanti, aplikasi harus
   sinkronisasi ulang ke Binance (posisi, pending order, TP/SL) sebelum
   mengizinkan aksi trading baru. Prinsip ini tetap berlaku meski
   implementasinya belum ada.

## Jika hanya akun GitHub yang perlu dipulihkan (HP tetap ada)

APK yang sudah terinstal di HP tetap berfungsi seperti biasa -- GitHub hanya
dibutuhkan untuk mengembangkan (push kode baru), bukan untuk menjalankan APK
yang sudah terpasang.

## Status implementasi

Sampai 2026-11-08, aplikasi belum punya penyimpanan data apa pun yang perlu
dipulihkan (belum ada API Key tersimpan, belum ada database trading) -- jadi
panduan di atas sebagian besar masih berupa prinsip untuk diikuti begitu
fitur-fitur itu dibangun, bukan langkah yang sudah bisa dijalankan hari ini.
