# TODO

## WAJIB sebelum lanjut apa pun
- [ ] Ikuti 7 langkah setup GitHub (akun -> repo privat `me-futures-ai-bot` -> Personal Access Token -> git di Termux).
- [ ] Push isi repo ini ke GitHub.
- [ ] Cek tab **Actions** -> workflow **Android Build** -> tempel hasilnya di sini (sukses/gagal + pesan error kalau gagal).
      Ini verifikasi PERTAMA kode Kotlin di `android/` -- belum pernah di-compile sama sekali sebelumnya.

## Setelah build pertama sukses -- lanjut bertahap (seperti Fase 1-2 Node.js dulu, tapi di Kotlin)
- [x] Signer.kt (HMAC-SHA256, murni, 6 JUnit test) -- porting dari `backend/src/exchange/signer.js`.
- [x] ReconnectingWebSocket.kt (auto-reconnect, backoff+jitter, 3 JUnit test) -- porting dari `backend/src/exchange/wsClient.js`.
- [ ] REST client Kotlin (signed request, belum ada -- baru WS).
- [ ] Exchange Gateway LENGKAP (REST+WS tergabung rapi dalam satu lapisan).
- [x] Unit test JUnit untuk logic murni -- SUDAH jalan otomatis di GitHub Actions (`gradle test`), terpisah dari `assembleDebug`.
- [ ] Foreground Service -- supaya bot bisa jalan di background sesuai kebijakan Android (bukan lagi soal Termux:Boot, tapi START_STICKY + foreground service type yang benar).
- [ ] **Auto-reconnect** WebSocket setelah koneksi putus (fitur yang diminta eksplisit).
- [ ] **Auto-start setelah HP restart** -- pakai `BOOT_COMPLETED` broadcast receiver (versi Android native dari Termux:Boot).
- [ ] Data layer (symbol registry, candle store) -- porting logic dari `backend/src/data/`.
- [ ] Simpan API Key terenkripsi pakai `EncryptedSharedPreferences`/Android Keystore (bukan `.env` lagi).

## Perlu konfirmasi Anda (keputusan desain, belum berubah dari sebelumnya)
- [ ] Mode Posisi Binance: One-way atau Hedge?
- [ ] Database: Room (SQLite Android) sebagai pengganti `better-sqlite3`/`sql.js` yang dulu direncanakan untuk Node.js.

## Fitur yang sudah disetujui (tetap berlaku, lihat PROJECT_MASTER.md §Fitur Tambahan Direkomendasikan)
Semua rekomendasi analisis/eksekusi/keamanan/UX yang sudah dicatat sebelumnya
tetap berlaku -- cuma diimplementasikan di Kotlin, bukan Node.js.
