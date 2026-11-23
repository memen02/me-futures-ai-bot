# Redesign Android ME Project

## Audit basis APK gabungan

Branch sumber `codex/satu-apk-mode-trading` pada commit `0a82dd11` berisi APK tunggal dengan pemilihan Demo/Mainnet. Dasbor, scanner, simulasi dan pengaturan sebelumnya merupakan empat tujuan dalam satu Activity. Pemilihan tab menggunakan indeks numerik; penambahan tujuan sebelum Akun berisiko membuka halaman yang salah saat Activity dipulihkan.

| Pemilik fungsi | Perilaku yang perlu dipertahankan | Batas perubahan |
|---|---|---|
| MainActivity dan TradingDesk | Memulihkan tab, menjaga grafik publik hanya ketika Beranda terlihat, memperbarui daftar Pasar, dompet, posisi dan jurnal Paper | Ubah presentasi dan rute dengan ID stabil |
| AccountPage dan CredentialVault | Memasang perlindungan layar, membersihkan input saat keluar, membaca/menulis key terenkripsi | Akses melalui Akun dan kontrol yang sudah ada |
| MarketScanner, PaperAccount, RiskPlan | Pemindaian pasar, simulasi lokal, jurnal dan batas risiko | Baca hasil lama melalui binding yang ada |
| DemoRunner, DemoExchange, LiveApproval | Order per venue, rekonsiliasi, persetujuan untuk dana asli | Pemilihan tab hanya menampilkan UI; panel persetujuan tetap di Akun |
| BotForegroundService, ReconnectingWebSocket dan PublicChartFeed | Monitoring latar belakang, reconnect, kuotasi dan candle publik | Pertahankan lifecycle, jumlah feed dan sumber |

## Tahap dan pemeriksaan

| Fase | Perubahan dan hasil yang bisa diperiksa | Risiko regresi dan pemeriksaan |
|---|---|---|
| 1 · Audit | Petakan dependensi UI, mode dan navigasi pada build gabungan; baseline memiliki build dan tes otomatis yang sudah lulus | Telusuri pemilihan tab, chart visibility, mode tersimpan dan otorisasi sebelum mengubah UI |
| 2 · Design system | Resource warna semantik dan dimensi, panel datar, tipografi sistem, warna status/sinyal/chart dan baris pasar dengan target sentuh 48dp. Identitas ME Project dipertahankan | Kesalahan tint atau tema pada perangkat; uji inflasi tema, warna semantik dan tinggi baris dalam Robolectric, lalu assemble APK |
| 3 · Navigasi | Lima tab. Pasar memakai scanner yang ada. Trading menampilkan dompet dan posisi Paper. Riwayat membuka jurnal Paper. Akun memuat sakelar Paper, pengaturan mode dan key lama. Tautan jurnal di Beranda menuju Riwayat | Uji setiap destinasi, akses kontrol, status selected, pemulihan Activity dan indeks lama, chart berhenti saat meninggalkan Beranda serta kunjungan tab tidak mengaktifkan trading |
| 4 · Beranda dan Pasar | Berikutnya: ranking, tabel harga/volume, ikon token dengan pemetaan, cache dan lazy loading | Uji sumber dan umur data, token ambigu, recycle row, kuota dan memori |
| 5 · Detail kontrak | Berikutnya: detail per simbol, grafik interaktif dan sinkronisasi order book | Uji simbol/timeframe, sequence gap, resync dan perlakuan data tidak tersedia |
| 6 · Explainability | Berikutnya: panel dari bukti yang tersedia dan overlay yang bisa ditelusuri ke data | Uji penanda/bar, arah, status data, pembatalan dan label skor |
| 7 · Trading dan Riwayat | Berikutnya: posisi/performa untuk mode bersangkutan, filter dan kurva jika data tersimpan memadai | Uji isolasi Paper/Demo/Mainnet, PnL dan statistik saat sampel kosong |
| 8 · Akun | Berikutnya: pengelompokan pengaturan strategi, risiko, preferensi dan keamanan | Uji migrasi, Keystore dan otorisasi per mode |
| 9 · Android | Berikutnya: pengujian HP fisik kelas 4 GB, font besar, lifecycle dan profiling | Ukur RAM, frame time, baterai dan durasi reconnect di perangkat |

Fase 4–9 menunggu persetujuan. Sampai Fase 3, Trading dan Riwayat menyajikan data Paper yang sudah dicatat aplikasi; bagian wallet, posisi dan jurnal diberi nama Paper agar sumbernya jelas meskipun badge venue menunjukkan Mainnet. Tidak ada angka pengganti untuk data bursa yang belum tersedia.
