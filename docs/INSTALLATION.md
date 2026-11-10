# INSTALLATION.md — Instalasi ME FUTURES AI BOT (Android, tanpa Termux)

> Dokumen ini ditulis ulang 2026-11-08. Versi lama mengarahkan ke instalasi
> Termux -- itu sudah TIDAK BERLAKU sejak proyek pivot ke aplikasi Android
> mandiri (2026-11-07). Jangan ikuti instruksi Termux dari sumber lain untuk
> proyek ini.

## 1. Dapatkan APK

Proyek ini dibangun lewat **GitHub Actions**, bukan di-compile di HP. Ada dua cara:

**A. Lewat GitHub CLI (`gh`), kalau Anda juga mengembangkan proyek ini:**
```bash
pkg install gh -y   # kalau belum ada
gh auth login
gh run list -R <owner>/me-futures-ai-bot
gh run download <run-id> -n me-futures-ai-bot-debug-apk -R <owner>/me-futures-ai-bot
```

**B. Lewat browser (kalau Anda hanya pengguna, bukan pengembang):**
1. Buka halaman **Actions** di repo GitHub proyek ini.
2. Pilih run terbaru yang hijau (sukses) untuk workflow **Android Build**.
3. Di bagian **Artifacts**, unduh `me-futures-ai-bot-debug-apk`.

## 2. Install APK di HP

1. File hasil unduhan ada di folder **Download** (`app-debug.apk` atau
   di dalam folder bernama sesuai artifact).
2. Buka aplikasi **Files**/**My Files**, cari file APK itu, ketuk.
3. Kalau muncul peringatan "Install blocked"/"Install unknown apps", ketuk
   **Settings**, izinkan untuk aplikasi yang dipakai membuka file ini, lalu
   kembali dan ketuk **Install** lagi.
4. Setelah terpasang, ketuk **Open**.

## 3. Verifikasi awal

Layar pertama yang muncul menunjukkan status koneksi (demo: stream publik
Binance BTCUSDT). Kalau statusnya berubah dari "Menyambungkan..." menjadi
"Tersambung", instalasi berhasil.

## 4. Fitur yang belum ada (jangan kaget)

Sesuai [`PROJECT_STATUS.md`](PROJECT_STATUS.md) dan
[`AUDIT_REPORT.md`](AUDIT_REPORT.md): belum ada input API Key, belum ada
analisis trading, belum ada notifikasi, dan koneksi belum bertahan kalau app
di-minimize (Foreground Service menyusul). Ini masih fondasi, bukan bot yang
siap dipakai trading.

## Untuk pengembang: setup push ke GitHub dari Termux

Termux di sini HANYA dipakai sebagai alat pengembangan (git, bukan untuk
menjalankan bot):

```bash
pkg install git gh -y
gh auth login
git config --global user.name "Nama Anda"
git config --global user.email "email@anda.com"
cd /path/ke/folder/proyek
git config --global --add safe.directory "$(pwd)"
git add . && git commit -m "pesan" && git push
gh run watch
```
