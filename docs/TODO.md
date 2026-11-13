# TODO

## Selesai (terverifikasi CI hijau, lihat CHANGELOG.md untuk nomor run)
- [x] Setup GitHub + push pertama.
- [x] Signer.kt (HMAC-SHA256, murni, 6 JUnit test) -- porting dari `backend/src/exchange/signer.js`.
- [x] ReconnectingWebSocket.kt (auto-reconnect, backoff+jitter) -- porting dari `backend/src/exchange/wsClient.js`.
- [x] OkHttpClient singleton (audit #5, 2026-11-08).
- [x] Foreground Service (`BotForegroundService.kt`) -- reconnect bertahan di background.
- [x] Tombol Start/Stop eksplisit (audit #3, 2026-11-10) -- service tidak lagi auto-start tanpa kontrol.
- [x] Bug `close()` tidak membatalkan reconnect terjadwal -- diperbaiki (audit #2, 2026-11-10), scheduler dibuat bisa diuji (`CloseCancelsReconnectTest.kt`), **tapi lihat catatan testing di bawah**.
- [x] `foregroundServiceType` diganti dataSync -> specialUse (audit #1, 2026-11-10) -- dataSync kena batas 6 jam/24 jam di Android 15+.
- [x] Notifikasi: `setContentIntent` supaya ketuk notifikasi membuka app.

## Menunggu keputusan/aset dari Anda
- [ ] **Ikon app kustom** -- sengaja belum dibuat, Anda ingin ganti sendiri.
- [ ] **Nama app** -- menunggu konfirmasi nama final (disebut "ME Project" di satu pesan, belum dikonfirmasi persis).
- [ ] Mode Posisi Binance: One-way atau Hedge?
- [ ] Database: Room (SQLite Android) sebagai pengganti `better-sqlite3`/`sql.js` yang dulu direncanakan untuk Node.js.

## Belum dibuat sama sekali
- [ ] **Boot Receiver** (`BOOT_COMPLETED`) -- auto-start service setelah HP restart. Perlu riset dulu pembatasan Android 14+/15+ untuk start foreground service dari boot (belum diriset, jangan asumsikan bebas aturan sama seperti dataSync kemarin).
- [ ] REST client Kotlin (signed request) -- baru ada WS, belum ada REST.
- [ ] Data layer (symbol registry, candle store) -- porting logic dari `backend/src/data/`.
- [ ] Penyimpanan API Key terenkripsi (Android Keystore/EncryptedSharedPreferences).
- [ ] Battery optimization exemption -- diminta otomatis dari dalam app (saat ini cuma panduan manual dari percakapan lama, belum ada kode).

## Celah pengujian yang jujur harus diakui
- [ ] **`CloseCancelsReconnectTest.kt` TIDAK menguji class `ReconnectingWebSocket` yang sungguhan** -- hanya menguji pola logika secara terisolasi (lihat komentar di file test-nya). Untuk benar-benar menguji class asli butuh MockWebServer atau Mockito (dependency baru, perlu dipertimbangkan risikonya dulu).
- [ ] Tidak ada `androidTest`/Robolectric sama sekali -- start/stop service, reconnect saat berjalan, lifecycle Activity/Service, notifikasi **tidak bisa diuji otomatis** dengan setup saat ini, wajib dites manual di device nyata tiap kali ada perubahan di area ini.

## Fitur yang sudah disetujui (tetap berlaku, lihat PROJECT_MASTER.md §Fitur Tambahan Direkomendasikan)
Semua rekomendasi analisis/eksekusi/keamanan/UX yang sudah dicatat sebelumnya
tetap berlaku -- cuma diimplementasikan di Kotlin, bukan Node.js.
