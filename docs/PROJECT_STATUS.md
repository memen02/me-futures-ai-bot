# PROJECT STATUS

Nama aplikasi Android adalah **ME Project**. Nomor build tersedia di tab Akun, bagian Perangkat.

## Status pengembangan per 28 November 2026

Basis pemeriksaan adalah `chatgpt/realtime-scanner` pada `7a5bfaa8`, turunan langsung checkpoint Fase 1 `5d2bd62f`. Tabel lama di bawah mencatat perjalanan implementasi. Bukti build terbaru tersedia pada workflow branch pengembangan.

|Area|Sudah ada dalam kode|Pekerjaan terbuka|
|---|---|---|
|Pasar dan riset AI|Scanner kontrak, ticker live, monitor Paper dan pilot riset BTCUSDT/ETHUSDT dengan candle 1m/5m, depth dan status WAIT.|Validasi end-to-end pada data nyata dan pengukuran RAM, CPU serta baterai di HP 4 GB.|
|Siklus Stop/Start|Dalam perubahan ini, tick scanner membuka sesi riset hanya saat bot aktif. Stop menutup WebSocket riset, permintaan snapshot yang sedang berjalan, cache dan worker. Start memakai sesi baru. Scanner harga dan monitor posisi tetap berjalan dengan siklusnya sendiri.|Uji layar terkunci, perpindahan jaringan, reboot dan 24–72 jam di perangkat. Waktu penerapan Stop bergantung pada tick service berikutnya dan penjadwalan Android.|
|Antrean order book riset|Sebelum diproses di Android, tiap koneksi depth riset menampung maksimal delapan pesan dengan total 262.144 karakter. Saat penuh, likuiditas sesi langsung dianggap invalid dan koneksi dibangun kembali untuk sinkronisasi snapshot.|Ukur frekuensi overflow dan waktu pemulihan pada jaringan perangkat nyata.|
|Akun, lisensi dan perangkat|Monitoring akun, pemisahan venue dan fondasi skema lisensi/pengikatan perangkat ada di repo.|Deployment backend, verifikasi UI/layanan dan pengelolaan lisensi end-to-end.|
|Order dan risiko|RiskPlan, jurnal intent, pemulihan dan pelindung SL/TP tersedia pada jalur yang ada. Sinyal pilot digunakan untuk riset.|Validasi Binance Demo, rekonsiliasi saat putus jaringan dan tinjauan seluruh jalur entry sebelum persetujuan Mainnet.|
|Backtest dan model|Komponen replay, evaluasi temporal dan pembelajaran tersedia untuk riset.|Dataset terverifikasi, kelengkapan funding, uji out-of-sample dan Paper minimal 30 hari. Bukti profitabilitas masih terbuka.|

Untuk menguji perubahan siklus riset, buka Pasar dengan bot mati dan periksa pembaruan harga. Nyalakan bot, tunggu bukti BTC/ETH, matikan bot hingga status riset OFFLINE, lalu nyalakan lagi. Pada awal sesi baru status harus WAIT sampai data sinkron kembali. Pantau juga posisi Paper yang sudah terbuka sepanjang langkah ini.

[Rincian gerbang rilis](FINAL-ROADMAP-AUDIT.md) · [Audit UI/performa](BATCH-11-UI-PERFORMANCE-AUDIT.md)

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

Dasbor, Scanner, Simulasi, dan Akun tersedia dari navigasi bawah. Tombol Mulai bot di tab Scanner
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
Pengaturan menyimpan pasangan API HMAC secara terenkripsi di HP dan menguji akses baca Binance Futures. Mode Mainnet dalam APK yang sama mendukung eksekusi BTC/USDT mainnet setelah persetujuan di HP. Pengujian dengan akun dan perangkat pengguna masih diperlukan.

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
