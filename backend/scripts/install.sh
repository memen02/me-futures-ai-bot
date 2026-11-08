#!/usr/bin/env bash
set -e

echo "== ME FUTURES AI BOT - Termux Install (Fase 1) =="

if ! command -v node >/dev/null 2>&1; then
  echo "Node.js belum terpasang. Jalankan: pkg install nodejs-lts"
  exit 1
fi
echo "Node version: $(node -v)"

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  cp .env.example .env
  echo ".env dibuat dari .env.example (BOT_MODE=paper secara default, paling aman)."
  echo "Edit .env bila perlu. JANGAN isi API Key produksi di sini - lihat docs/SECURITY.md."
fi

npm install

echo ""
echo "Selesai. Langkah berikutnya:"
echo "  npm test          # jalankan unit test (tanpa jaringan)"
echo "  npm run selftest   # verifikasi boot singkat"
echo "  npm start          # jalankan bot (Ctrl+C untuk berhenti)"
