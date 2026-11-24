# Kontrak keputusan dan arsitektur AI ME Project V3

## Jalur Fase 1

|Komponen|Masukan dan keputusan|Pemanggil atau keluaran|
|---|---|---|
|MarketData dan MarketModel|Perubahan harga 24 jam, lokasi harga dalam rentang, deviasi dari harga rata-rata tertimbang, serta bias. Gradien memakai ticker ronde sebelumnya dan label ronde berikutnya. Warm-up 30 ronde.|DeskStore menyimpan bobot dan ronde. Score hanya nilai arah model.|
|CandleHistory dan SmaSignal|Candle 1m tertutup yang kontinu dan cukup untuk 20 penutupan 5m. Bandingkan penutupan terhadap SMA20 1m dan 5m pada waktu keputusan.|BUY atau SELL ketika keduanya searah; riwayat belum siap atau konflik memberi WAIT.|
|BaselineDecision, versi logit-sma20-v1|Ticker tepat waktu, kontrak USDT, quote volume 24 jam minimal satu juta, skor valid. Skor >0,57 untuk BUY atau <0,43 untuk SELL harus searah SMA. Kesetaraan ambang memberi WAIT.|TokenSignals untuk detail, MarketScanner untuk kandidat Paper dan DemoRunner untuk keputusan bursa memakai kontrak yang sama.|
|RiskPlan dan pemeriksaan adapter|Quote masuk, spread, SL dan TP struktural dari 20 bar, slippage, komisi, ekuitas tersedia, anggaran rugi, RR bersih, filter kontrak, kondisi akun dan funding menurut mode.|Sinyal arah dapat lolos di detail dan tetap ditolak sebelum eksekusi. Alasannya dicatat sebagai status.|
|Pelaksanaan dan pemantauan|PaperAccount membukukan fill virtual. DemoRunner mengirim dan merekonsiliasi order BTCUSDT pada Demo jika diaktifkan. Pemeriksaan Mainnet mendahului jalur entry.|ScreeningService menjalankan scanner dan monitor Paper. BotForegroundService menjalankan runner dan feed mark untuk intent bursa.|

**Contoh kontrak.** Jika kedua SMA menunjuk BUY namun skor 0,50, hasil analisis WAIT. Jika skor 0,62 dan kedua SMA menunjuk BUY, hasil analisis BUY dapat diteruskan ke RiskPlan. RR bersih 1,8 menggagalkan entry. Angka ini contoh pengujian logika, tanpa pernyataan peluang kemenangan.

Kontrak menyamakan keputusan untuk masukan identik. Perbedaan candle, likuiditas dan saldo antarvenue bisa menghasilkan keluaran akhir berbeda. Scanner Paper menyaring banyak simbol dan memperdalam satu kandidat, sementara runner exchange menangani BTCUSDT. ManualPaper merupakan tiket manual terpisah. Ini penting ketika membandingkan jurnal antar mode.

## Desain berikutnya untuk persetujuan

1. Bentuk snapshot menyertakan simbol, venue, waktu event dan terima, status gap, timeframe, candle terakhir yang ditutup, dan versi transformasi. Putuskan WAIT bila masukan wajib hilang.
2. Pisahkan regime arah, volatilitas dan kejadian seperti retest. Setiap klasifikasi membawa bukti pendukung, konflik dan invalidasi. Konfirmasi reversal perlu struktur dan kelanjutan pada candle tertutup.
3. Registry strategi mengikat ID dan versi ke syarat aktivasi, entry, exit, invalidasi, biaya serta status penelitian. Gunakan shadow per kandidat sebelum memberi akses Paper.
4. Kelompokkan bukti tren, momentum, struktur, volume/order flow, likuiditas, dan derivatif/konteks. Setiap kelompok menyumbang maksimal satu suara. Simpan nilai, arah, validitas dan alasan. Ukur ketergantungan antarkelompok secara empiris.
5. Bandingkan syarat lima dan enam kelompok terhadap baseline pada walk-forward. Laporkan coverage dan kesempatan yang ditolak bersama expectancy sesudah biaya. Simpan data yang tidak tersedia secara eksplisit.
6. Bekukan model pemenang dan parameter risiko. Challenger dinilai dengan data temporal terpisah. Kalibrasi probabilitas memerlukan uji reliabilitas serta Brier/log loss di luar sampel. Drift dapat menonaktifkan model atau mengembalikan versi terakhir tervalidasi. Kewenangan model berakhir sebelum veto risiko dan izin venue.

Panel token saat ini menjelaskan baseline dan status data. Regime lanjutan, probabilitas terkalibrasi, registry lengkap, atribusi enam kelompok dan statistik OOS memerlukan fase selanjutnya.
