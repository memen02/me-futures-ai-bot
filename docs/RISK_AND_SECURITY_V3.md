# Risiko posisi dan keamanan Fase 1

## Saat posisi sudah terbuka

|Kondisi|Tindakan implementasi|Risiko yang tersisa|
|---|---|---|
|Intent entry telah dicatat|Simpan ID dan menit sebelum market request. Setelah timeout, baca order, posisi dan algo menurut venue dan ID.|Jika status tidak diketahui dan order belum tampak, aplikasi menyimpan intent dan menunjukkan WAIT. Pemeriksaan manual di akun bursa mungkin diperlukan.|
|Entry ditolak definitif|Untuk -1111, -2019, -4164, simpan penolakan dan matikan aktivasi. Lepaskan intent setelah verifikasi posisi kosong, ID tidak ditemukan, dan tanpa order BTC aktif atau pelindung milik intent.|Penolakan lain tetap konservatif. Jika ACK penolakan hilang, klien kehilangan dasar untuk menyatakan entry ditolak.|
|Ada posisi|Bandingkan arah, ID, kuantitas fill dan kepemilikan. Intent baru juga terikat sidik jari API key. Kasus lama Demo tanpa sidik jari memakai pemeriksaan order dan kuantitas.|Akun khusus diperlukan. Posisi searah dari sumber luar membuat atribusi sulit.|
|SL/TP|Simpan penanda sebelum POST Algo. Verifikasi kedua ID, sisi, tipe, mark price, trigger dan closePosition melalui daftar algo aktif.|Eksekusi entry dan pemasangan dua pelindung tidak atomik. Putus jaringan pada jendela itu bisa meninggalkan eksposur sampai bursa dapat diperiksa.|
|Partial fill|Verifikasi pelindung untuk posisi yang terisi lalu batalkan sisa order dan periksa lagi. Exit menunggu order entry terminal.|Balasan cancel dapat bersilangan dengan fill. REST hanya menunjukkan keadaan saat snapshot.|
|Proteksi salah, ditolak atau hilang|Matikan entry baru, simpan intent exit, coba reduce-only pada posisi milik aplikasi. Simpan ID exit sebelum kirim. Cek status terminal dan sisa kuantitas sebelum percobaan berikutnya.|Exit market bisa mengalami slippage, reject atau timeout. WAIT berarti konfirmasi lanjutan diperlukan.|
|Stop/restart|Stop membatalkan izin entry. Runner tetap mengurus intent dan posisi yang sudah dimiliki. Scanner independen. BootReceiver menghormati preferensi perangkat.|Force-stop, Doze, larangan foreground service dan kehilangan jaringan dapat menghentikan klien.|
|Paper|Exit dinilai memakai sisi bid/ask, trailing sesuai preferensi. Simpan kuotasi pemicu dan alasan sebelum mengambil riwayat funding, lalu bukukan sekali.|Gap saat perangkat mati tidak dapat direkonstruksi sebagai fill pasti. Funding yang belum direkonsiliasi menunda hasil final.|
|Harga dan status|Mark-price publik terurut memberi valuasi. Status private order dan kepemilikan berasal dari REST bertanda tangan. Jurnal dipisah per venue.|Mark publik tidak membuktikan order sudah fill atau sudah terlindungi. User-data stream masih perlu integrasi.|

## Batas risiko dan rahasia

RiskPlan memakai risiko 0,5%, anggaran harian 1,5% UTC, taksiran slippage 0,02% per fill, biaya dasar 0,05% per sisi, serta RR bersih minimal 2. Spread untuk entry dibatasi 0,05%. Sizing dibatasi jarak stop dan modal. Harga eksekusi setelah gap dapat melampaui anggaran. Correlation guard portofolio, batas mingguan, partial TP, ATR adaptif dan veto drawdown menyeluruh memerlukan pengujian lanjutan.

Mainnet menolak entry baru di runner dan fungsi pengiriman market. Jurnal lama milik akun yang sama masih dapat dipulihkan, termasuk proteksi dan reduce-only. Mode dan penggantian kredensial dibatasi saat ada intent. Penyimpanan memakai Android Keystore dan berkas terenkripsi terpisah Demo/Real. Penandatanganan HMAC dan HTTPS berada di perangkat. Tolak aktivasi bila status izin withdrawal yang diwajibkan tidak dapat diverifikasi. Gunakan hanya kredensial fixture dalam CI. APK rilis ditandatangani offline dengan identitas upgrade yang sama.

Jika aplikasi menunjukkan status ambigu, periksa posisi, order reguler dan algo pada venue serta ID yang benar. Pastikan proteksi masih aktif dan amati kuantitas aktual sebelum mengaktifkan kembali bot. Menghapus data aplikasi atau mengganti key dapat menghilangkan hubungan dengan intent. Simpan log diagnostik tanpa secret, header API atau URL bertanda tangan.

Dokumentasi acuan ditinjau pada 24 November 2026. Perubahan endpoint dapat memerlukan audit baru. [Binance USDⓈ-M Trade API](https://developers.binance.com/en/docs/catalog/core-trading-derivatives-trading-usd-s-m-futures/api/rest-api/trade#new-algo-order) · [Android foreground services](https://developer.android.com/develop/background-work/services/fgs) · [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
