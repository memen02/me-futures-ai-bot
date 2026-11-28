# ME Project — Roadmap pembangunan

Status: Batch 13 / Step 6. Estimasi 75–80% menuju APK Android siap diuji, bukan hasil audit terukur atau kesiapan real trading.

| Step | Pekerjaan | Status / syarat selesai |
|---|---|---|
| 1 | Fondasi Kotlin, WebSocket, foreground service | Dibuat; regresi perangkat |
| 2 | Scanner live | Dibuat; validasi data dan reconnect |
| 3 | Strategi dan indikator | Validasi backtest, konfirmasi independen |
| 4 | Paper trading | Validasi jurnal, TP/SL, biaya dan slippage |
| 5 | Dashboard lima tab | Dibuat; CI navigasi pending |
| 6 | Autentikasi, biometrik, Owner | Aktif; verifikasi CI, sesi, logout, akses ditolak tanpa approval |
| 7 | Keamanan trading | Audit order, risiko 0.5–1%, RR min 1:2, leverage <=20x, rekonsiliasi dan stop |
| 8 | Lisensi dan perangkat | Backend HTTPS, approval Owner, expiry, revoke, satu perangkat aktif, migrasi HP |
| 9 | Audit regresi | Semua tes lulus, audit duplikasi, keamanan dan RAM |
| 10 | APK | Build, signing, instalasi dan uji HP |
| 11 | Audit akhir | Validasi backend dan distribusi; real trading tetap terkunci sampai validasi dan persetujuan |

## CI blocker
Lima run gagal pada dua tes Debug DeskNavigationTest. Commit d0d627b memperbarui fixture autentikasi; hasil CI baru belum terverifikasi. Jangan menghapus tes atau melemahkan gate produksi.

## Fase berikutnya: iOS
ME Project dan ME Owner ditargetkan untuk Android dan iOS, satu backend dan identitas Owner yang sama. Migrasi perangkat harus mencabut akses perangkat lama. Arsitektur background iOS dan packaging panel terpisah masih perlu keputusan teknis. Tidak termasuk estimasi Android.

## Aturan pelaporan
Setiap perubahan: periksa kode dan audit, perbaiki temuan terverifikasi, tambah regresi bila diperlukan, commit, cek CI, catat blocker. Bedakan dibuat, diuji, dan siap distribusi. Jangan mengklaim APK siap tanpa hasil build dan uji perangkat.
