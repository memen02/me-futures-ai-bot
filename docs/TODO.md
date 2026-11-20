# TODO

## Selesai (terverifikasi CI hijau, lihat CHANGELOG.md untuk nomor run)
- [x] Setup GitHub + push pertama.
- [x] Signer.kt (HMAC-SHA256, murni, 6 JUnit test) -- porting dari `backend/src/exchange/signer.js`.
- [x] ReconnectingWebSocket.kt (auto-reconnect, backoff+jitter) -- porting dari `backend/src/exchange/wsClient.js`.
- [x] OkHttpClient singleton (audit #5, 2026-11-08).
- [x] Foreground Service (`BotForegroundService.kt`) -- reconnect bertahan di background.
- [x] Tombol Start/Stop eksplisit (audit #3, 2026-11-10) -- service tidak lagi auto-start tanpa kontrol.
- [x] Bug `close()` tidak membatalkan reconnect terjadwal -- diperbaiki (audit #2, 2026-11-10), scheduler dibuat bisa diuji (`CloseCancelsReconnectTest.kt`).
- [x] `foregroundServiceType` diganti dataSync -> specialUse (audit #1, 2026-11-10) -- dataSync kena batas 6 jam/24 jam di Android 15+.
- [x] Notifikasi: `setContentIntent` supaya ketuk notifikasi membuka app.
- [x] Guard Stop saat belum running (audit mandiri 2026-11-13) -- cegah koneksi "kedip".
- [x] Izin notifikasi dipindah ke onCreate() (audit mandiri 2026-11-13) -- cegah race dengan startForegroundService().

- [x] Penyimpanan kredensial HMAC di Android Keystore (kunci AES) dan uji akses Futures Binance.

## Menunggu keputusan/aset dari Anda
- [ ] Mode Posisi Binance: One-way atau Hedge?
- [ ] Database: Room (SQLite Android) sebagai pengganti `better-sqlite3`/`sql.js` yang dulu direncanakan untuk Node.js.

## Belum dibuat sama sekali
- [ ] Rekonsiliasi posisi dan saldo melalui REST privat.
- [ ] Penyimpanan riwayat candle multi-simbol lintas sesi -- porting logic dari `backend/src/data/`.
- [ ] Battery optimization exemption -- diminta otomatis dari dalam app (saat ini cuma panduan manual dari percakapan lama, belum ada kode).

## Verifikasi di perangkat
- [ ] Jalankan [skenario pemantauan di HP](TEST_PLAN.md#uji-pemantauan-di-hp) untuk Auto Start, Stop, dan umur harga.
- [ ] Tidak ada `androidTest`/Robolectric sama sekali -- start/stop service, reconnect saat berjalan, lifecycle Activity/Service, notifikasi **tidak bisa diuji otomatis** dengan setup saat ini, wajib dites manual di device nyata tiap kali ada perubahan di area ini.

## Fitur yang sudah disetujui (tetap berlaku, lihat PROJECT_MASTER.md §Fitur Tambahan Direkomendasikan)
Semua rekomendasi analisis/eksekusi/keamanan/UX yang sudah dicatat sebelumnya
tetap berlaku -- cuma diimplementasikan di Kotlin, bukan Node.js.
