# PROJECT STATUS

Nama aplikasi Android adalah **ME Project**. Nomor build terlihat di layar utama.

**Fase saat ini:** scanner pasar publik dan simulasi transaksi otomatis pada perangkat Android.
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
| Sinyal SMA 20 | Indikator pemantauan BTC/USDT. BUY saat penutupan terakhir 1m dan 5m masing-masing di atas SMA 20 pada intervalnya, SELL bila keduanya di bawah. WAIT untuk kondisi lain, termasuk riwayat yang belum menyusul menit bursa. |
| Layanan dan reboot | Foreground Service memegang koneksi. BootReceiver memeriksa Auto Start dan pilihan Start/Stop terakhir pengguna. Perilaku sistem Android perlu diuji di HP. |
| Penandatanganan | Signer Kotlin menyediakan HMAC-SHA256 dengan unit test. |

## Scanner pasar dan simulasi

Dasbor, Scanner, Simulasi, dan Akun tersedia dari navigasi bawah. Tombol Start
menjalankan foreground service. Selama layanan berjalan, scanner mengambil
statistik bergulir 24 jam dari seluruh kontrak perpetual aktif Binance USDⓈ-M
sekitar sekali per menit. Daftar kontrak berasal dari exchangeInfo dan
disegarkan tiap jam. Simbol tanpa ticker tetap tercantum. Koneksi yang gagal
atau data kedaluwarsa ditandai di layar.

Model regresi logistik pada HP belajar dari perubahan harga antara dua scan
berturutan. Masukannya adalah persentase perubahan, posisi harga dalam rentang
24 jam, dan selisih dari harga rata-rata tertimbang. Skor arah mulai tampil
setelah 30 ronde yang memiliki label. Skor ini belum dikalibrasi sebagai
probabilitas laba.

Simulasi bersifat opsional. Saldo awal 1.000 USDT virtual, maksimum satu posisi
100 USDT, dengan biaya asumsi 0,05% pada tiap sisi. Kandidat perpetual USDT
bervolume minimal satu juta USDT per 24 jam harus memiliki skor di atas 0,57
atau di bawah 0,43, lalu mendapat konfirmasi candle tertutup melalui SMA 20
pada 1m dan 5m. Harga entry simulasi diambil pada scan sesudah konfirmasi.
Pada scan berikutnya posisi ditutup bila PnL kotor mencapai −1 atau +2 USDT,
durasi mencapai 30 menit, atau simulasi dinonaktifkan. Eksekusi diskret ini
belum memperhitungkan funding, slippage, atau likuidasi. Jika layanan berhenti,
pemeriksaan posisi dilanjutkan saat layanan berjalan kembali. Model, saldo,
posisi, dan 75 catatan jurnal terakhir disimpan di HP.

## Pekerjaan berikutnya

Pengujian jalan terus 24 jam di HP, penilaian model pada data di luar periode
pelatihan, riwayat candle lintas sesi, dan rekonsiliasi akun Binance privat.
Eksekusi uang asli dan penyimpanan API key belum tersedia.

## Menunggu dari Anda

- Mode Posisi (One-way/Hedge), pilihan database (Room).

## Yang TIDAK berubah

Aturan trading, risk management, keamanan API Key, fitur rekomendasi yang
sudah disetujui -- semua tetap berlaku, cuma bahasa implementasinya Kotlin.
Temuan API Binance (migrasi Algo Order, split WS /public//market//private)
tetap relevan untuk diimplementasikan nanti.

## Berikutnya

Uji layanan, reboot, dan pemulihan candle di HP. Setelah riwayat terverifikasi,
uji hasil simulasi model dan aturan SMA 20 pada data historis yang terpisah.
