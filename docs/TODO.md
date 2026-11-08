# TODO

## WAJIB sebelum lanjut apa pun
- [ ] Ikuti 7 langkah setup GitHub (akun -> repo privat `me-futures-ai-bot` -> Personal Access Token -> git di Termux).
- [ ] Push isi repo ini ke GitHub.
- [ ] Cek tab **Actions** -> workflow **Android Build** -> tempel hasilnya di sini (sukses/gagal + pesan error kalau gagal).
      Ini verifikasi PERTAMA kode Kotlin di `android/` -- belum pernah di-compile sama sekali sebelumnya.

## Setelah build pertama sukses -- lanjut bertahap (seperti Fase 1-2 Node.js dulu, tapi di Kotlin)
- [ ] Exchange Gateway versi Kotlin (REST+WS Binance) -- porting logic dari `backend/src/exchange/` (referensi, bukan dicopy mentah -- bahasa beda).
- [ ] Unit test JUnit untuk logic murni (signer HMAC, parsing JSON) -- jalan di JVM biasa, tidak butuh emulator, dites otomatis di GitHub Actions.
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
