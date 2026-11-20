# PROJECT STATUS

**Fase saat ini:** pemantauan pasar publik di aplikasi Android mandiri.
Pengujian Stop, notifikasi, jaringan, dan reboot pada HP untuk revisi ini masih tertunda.
Lihat [skenario uji perangkat](TEST_PLAN.md#uji-pemantauan-di-hp).

## PERUBAHAN ARSITEKTUR BESAR (2026-11-07)

Atas permintaan eksplisit: proyek berubah dari "backend Node.js di Termux +
APK monitor terpisah" menjadi **satu aplikasi Android Kotlin mandiri**
(tanpa Termux sama sekali untuk menjalankan bot).

- **Pendekatan**: Kotlin murni ditulis ulang, BUKAN membungkus Node.js lama.
- **`backend/` (Node.js, 55 test)** tetap di repo sebagai **referensi desain**, TIDAK dijalankan.

## Implementasi dan verifikasi

| Komponen | Kode dan verifikasi |
|---|---|
| Build Android | Unit test JVM dan build APK debug lulus untuk commit `dad1ed1`. [Hasil build](https://github.com/memen02/me-futures-ai-bot/actions/runs/44701605795). |
| Koneksi pasar | WebSocket memakai backoff dan satu OkHttpClient. Tes kelas asli mencakup Stop, callback terlambat, pembatalan reconnect, dan reconnect normal. |
| Tampilan BTC/USDT | Menampilkan bid, ask, dan waktu bursa. Tes memeriksa pembacaan harga, data tidak valid, dan kedaluwarsa. |
| Riwayat BTC/USDT | REST publik `GET /fapi/v1/klines` memuat 120 candle tertutup 1 menit. Data disimpan di RAM selama layanan berjalan dan dimuat ulang saat Start. |
| Layanan dan reboot | Foreground Service memegang koneksi. BootReceiver memeriksa Auto Start dan pilihan Start/Stop terakhir pengguna. Perilaku sistem Android perlu diuji di HP. |
| Penandatanganan | Signer Kotlin menyediakan HMAC-SHA256 dengan unit test. |

## Yang BELUM dibangun sama sekali

REST untuk akun Binance, symbol registry, penyimpanan candle lintas sesi, indikator, signal engine,
risk management, order execution, TP/SL, anti-duplicate, penyimpanan API Key,
battery optimization exemption otomatis, LIVE MODE lock di
Kotlin, paper trading, jurnal/PnL, dashboard lengkap. Rincian lengkap:
[`AUDIT_REPORT.md`](AUDIT_REPORT.md) dan [`TODO.md`](TODO.md).

## Menunggu dari Anda

- Ikon app kustom (sengaja belum dibuat).
- Konfirmasi nama app final.
- Mode Posisi (One-way/Hedge), pilihan database (Room).

## Yang TIDAK berubah

Aturan trading, risk management, keamanan API Key, fitur rekomendasi yang
sudah disetujui -- semua tetap berlaku, cuma bahasa implementasinya Kotlin.
Temuan API Binance (migrasi Algo Order, split WS /public//market//private)
tetap relevan untuk diimplementasikan nanti.

## Berikutnya

Uji layanan, reboot, dan pemulihan candle di HP. Setelah riwayat terverifikasi,
lanjutkan ke indikator dan pengujian pada candle tertutup.
