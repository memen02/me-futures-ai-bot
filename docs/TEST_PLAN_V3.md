# Rencana uji dan batas bukti V3

## Checklist penerimaan engineering Fase 1

Hitung satu poin per baris. Baris otomatis hanya diberi poin setelah workflow pada revisi yang akan didistribusikan lulus. Pengukuran di perangkat, akun Binance dan validasi strategi dilaporkan terpisah.

|No.|Hasil yang diperlukan|Cara memeriksa|
|---|---|---|
|1|Audit dan lima dokumen tersedia|Periksa tabel baseline, roadmap, arsitektur, risiko, dokumen uji pada branch pengembangan.|
|2|Otorisasi Mainnet lama tidak bisa mengirim entry|MainnetExecutionTest menguji runner dan blokir langsung pada adapter dengan HTTP terintersep.|
|3|Kontrak BUY/SELL/WAIT konsisten|BaselineDecisionTest menguji konflik, data invalid, ambang dan kesesuaian arah. Periksa pemanggil scanner, TokenSignals dan runner.|
|4|Pemantauan posisi Paper terpisah|PositionMonitorTest menguji urutan dan umur event, latch kuotasi dan pemulihan settlement. Periksa pemasangan monitor di ScreeningService.|
|5|Restart, ACK hilang dan partial fill direkonsiliasi|DemoRecoveryTest memeriksa ID SL yang sama, exit ambigu, pembatalan remainder, cleanup dan jurnal.|
|6|Kontrol order Demo dan kepemilikan|MainnetExecutionTest mencakup fixture Demo entry dengan SL/TP, penolakan margin, posisi legacy dan penolakan posisi asing.|
|7|Suite Android dan build artifact berhasil|Actions menjalankan gradle test dan :app:assembleReal, memeriksa label/paket dan flag debug. Catat jumlah JUnit tiap varian dari log.|
|8|APK distribusi sesuai identitas upgrade|Verifikasi digest arsip Actions, paket com.mefuturesaibot.app.real, versionCode dan tanda tangan APK akhir terhadap sertifikat distribusi sebelumnya.|

Jalankan dari direktori android memakai JDK 17 dan Gradle 8.13. Perintah setara CI adalah `gradle test --no-daemon --stacktrace` lalu `gradle :app:assembleReal --no-daemon`. Tes backend referensi dijalankan dari backend dengan `node --test`. [Riwayat workflow branch](https://github.com/memen02/me-futures-ai-bot/actions/workflows/android-build.yml?query=branch%3Acodex%2Fv3-phase1-position-safety)

## Tingkat bukti

|Sumber|Yang dapat dinilai|Batas|
|---|---|---|
|JUnit / Robolectric / HTTP fixture|Rumus, keputusan, transisi request, exception, penulisan state dan restore, isolasi mode, UI JVM|Fixture memakai nilai buatan berlabel pengujian dan tidak menghubungi akun Binance.|
|WebSocket publik|Sampel stream BTCUSDT depth5@500ms dan markPrice@1s diterima dari host publik utama dan Demo. Empat kombinasi menghasilkan depthUpdate/markPriceUpdate.|Pemeriksaan konektivitas singkat. Tanpa kredensial, fill, kuota produksi atau soak test.|
|Binance Demo terautentikasi|Perlu cocokkan pengiriman, ACK, fill, SL/TP, cancel, reduce-only, komisi dan funding ke laporan Demo.|Belum dilakukan pada Fase 1. Uji ini memakai akun Demo khusus pada Fase 10.|
|Uji strategi|Perlu baseline versus challenger, OOS, biaya, funding, drawdown dan interval statistik.|Fase 1 tidak menghasilkan win rate atau bukti profitabilitas.|

## Matriks HP fisik RAM 4 GB

Seluruh delapan skenario di bawah menunggu perangkat fisik. Catat versi Android/OEM, versionCode, waktu UTC, mode, kondisi jaringan, status proteksi dan insiden tanpa menyimpan rahasia.

|No.|Skenario|Kriteria lulus|
|---|---|---|
|1|Upgrade instalasi lama dan navigasi|Data Paper dan jurnal tetap terbaca, sertifikat diterima, Mainnet terkunci.|
|2|Start/Stop Paper saat screening|Scanner tetap berjalan, entry otomatis baru berhenti, posisi lama masih ditangani.|
|3|Kunci layar dan minimize|Status sinkronisasi dan usia data sesuai keadaan, service/notification sesuai versi Android.|
|4|Wi-Fi ke seluler dan sebaliknya|Reconnect, tidak ada event mundur yang dipakai, tidak ada order ganda.|
|5|Putus jaringan saat posisi dan pulihkan|Status stale/WAIT, rekonsiliasi pulih, harga lama tidak dipakai sebagai harga sekarang.|
|6|Reboot dengan auto-start hidup atau mati|Perilaku sesuai pilihan pengguna, intent tersimpan, tidak terjadi entry dari izin lama.|
|7|Low memory atau proses dihentikan|Jurnal dan ID pulih, posisi asing tetap aman, PnL dibukukan sekali.|
|8|Operasi 24 jam dengan RAM 4 GB|Catat RAM puncak, CPU, konsumsi baterai, thermal, ANR/crash, durasi gap dan keberhasilan reconnect.|

Tahap Paper minimal 30 hari menggunakan modal virtual yang dinyatakan, misalnya 100 USDT setelah pengaturan tersebut tersedia. Rekam semua peluang WAIT dan transaksi, metrik setelah biaya dan periode kehilangan koneksi. Pisahkan drawdown intraperiode dari hasil bersih bulanan. Tuliskan ukuran sampel sebelum membuat penilaian strategi.
