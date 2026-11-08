# INSTALLATION.md — Instalasi di Termux

## 1. Pasang Termux

Gunakan Termux dari **F-Droid** atau GitHub Releases resmi Termux, **bukan**
Google Play Store (versi Play Store sudah lama tidak diperbarui oleh pengembang
Termux).

## 2. Siapkan paket dasar

```bash
pkg update && pkg upgrade -y
pkg install -y nodejs-lts git
node -v   # pastikan >= 18
```

## 3. Clone / salin proyek

```bash
git clone <url-repo-anda> me-futures-ai-bot
cd me-futures-ai-bot
```

Jika belum punya repo GitHub, salin folder proyek ini langsung ke Termux
(mis. lewat `termux-setup-storage` + salin dari penyimpanan bersama).

## 4. Jalankan installer

```bash
bash backend/scripts/install.sh
```

Script ini akan: cek Node.js, membuat `.env` dari `.env.example` bila belum
ada, lalu menjalankan `npm install`.

## 5. Verifikasi

```bash
cd backend
npm test          # harus 7/7 lulus, tanpa jaringan
npm run selftest   # boot singkat, keluar otomatis jika semua OK
npm start          # jalankan bot (Ctrl+C untuk berhenti)
```

## 6. Isi konfigurasi

Edit `backend/.env`:
- `BOT_MODE=paper` (default, paling aman) atau `testnet`.
- **Jangan** isi API Key/Secret produksi di `.env` — itu nanti lewat APK
  Android (Fase 10). Untuk pengembangan/testnet lokal boleh diisi sementara.

## Opsional: Termux\:Boot (agar bisa start otomatis saat HP boot)

Akan dibahas detail di Fase 11 (Watchdog & Auto Recovery), termasuk panduan
battery optimization supaya proses tidak dimatikan Android di latar belakang.
