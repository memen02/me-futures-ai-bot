# ME Project — Roadmap pembangunan

Status: Batch 13 / Step 6. Estimasi 75–80% menuju APK Android siap diuji, bukan hasil audit terukur atau kesiapan real trading.

| Step | Pekerjaan | Status / syarat selesai |
|---|---|---|
| 1 | Fondasi Kotlin, WebSocket, foreground service | Dibuat; regresi perangkat |
| 2 | Scanner live | Dibuat; validasi data dan reconnect |
| 3 | Strategi dan indikator | Validasi backtest, konfirmasi independen |
| 4 | Paper trading | Validasi jurnal, TP/SL, biaya dan slippage |
| 5 | Dashboard lima tab | Dibuat; CI navigasi lulus, uji perangkat pending |
| 6 | Autentikasi, biometrik, Owner | Aktif; verifikasi CI, sesi, logout, akses ditolak tanpa approval |
| 7 | Keamanan trading | Audit order, risiko 0.5–1%, RR min 1:2, leverage <=20x, rekonsiliasi dan stop |
| 8 | Lisensi dan perangkat | Backend HTTPS, approval Owner, expiry, revoke, satu perangkat aktif, migrasi HP |
| 9 | Audit regresi | Semua tes lulus, audit duplikasi, keamanan dan RAM |
| 10 | APK | Build, signing, instalasi dan uji HP |
| 11 | Audit akhir | Validasi backend dan distribusi; real trading tetap terkunci sampai validasi dan persetujuan |

## CI blocker
Regresi navigasi sebelumnya diperbaiki. Runs 46175515157, 46175338841, 46175248721 dan 46175248694 lulus. Uji perangkat dan deployment tetap pending. Jangan melemahkan gate produksi.

## Fase berikutnya: iOS
ME Project dan ME Owner ditargetkan untuk Android dan iOS, satu backend dan identitas Owner yang sama. Migrasi perangkat harus mencabut akses perangkat lama. Arsitektur background iOS dan packaging panel terpisah masih perlu keputusan teknis. Tidak termasuk estimasi Android.

## Aturan pelaporan
Setiap perubahan: periksa kode dan audit, perbaiki temuan terverifikasi, tambah regresi bila diperlukan, commit, cek CI, catat blocker. Bedakan dibuat, diuji, dan siap distribusi. Jangan mengklaim APK siap tanpa hasil build dan uji perangkat.

## Distribusi Owner — persyaratan disetujui
- Repository saat ini public (diverifikasi via GitHub API); jangan mengunggah APK Owner khusus, signing keys, recovery secrets atau kredensial ke repository public.
- Target APK Owner terpisah dengan package ID berbeda, distribusi melalui repository GitHub private yang hanya dapat diakses Owner dan pihak yang secara eksplisit diberi akses. Owner dapat mengunduh rilis sendiri. Repository private belum dibuat; jangan ubah visibility repository saat ini tanpa persetujuan.
- Satu perangkat Owner aktif lintas Android/iOS; migrasi dengan verifikasi tambahan, pencabutan sesi/perangkat lama secara atomik dan recovery aman. Backend dan data akun tetap sama.
- Step 6: device proof Owner dan revocation setelah process death masih terbuka. Step 8: validasi migrasi dan recovery. Step 10: packaging, signing, private distribution dan pengujian akses unduhan. Tidak boleh dianggap selesai sebelum implementasi dan pengujian.

## Owner disaster recovery — explicit requirement
- Owner approved making the existing repository private at distribution finalization; visibility remains unchanged for now.
- Lost phone, factory reset, uninstall and damaged device must not permanently lock out Owner. Private downloadable signed APK releases must remain available independently of the original phone.
- Preserve signing identity securely off-device, never commit private signing keys. Maintain independent GitHub account recovery and ME Project recovery credentials.
- Recover on a new device without the old phone using independently stored recovery credentials; require additional verification, rate limits, one-time recovery credentials stored as hashes, atomic old-device/session revocation and audit trail. No password-only recovery bypass.
- Back up backend database securely and test restore, including accounts, licenses and approvals. APK download alone does not restore server data or grant access.
- Acceptance tests: lost-device recovery, old device denied, replay denied, reinstall, signed APK update, backup restore and private download access. Implementation and deployment pending.
