# SECURITY.md

> Ditulis ulang 2026-11-08 untuk arsitektur Android mandiri (tanpa Termux/
> backend terpisah). Bagian lama yang menjelaskan `src/core/config` (Node.js)
> dipindah ke bawah sebagai catatan referensi -- itu bukan kode yang dipakai
> aplikasi sekarang.

## Kredensial Binance di Android

Tab Akun menyimpan pasangan HMAC milik pengguna dalam satu berkas privat di `noBackupFilesDir`. Android Keystore menyimpan kunci AES-256. Setiap penyimpanan mengenkripsi ulang pasangan dengan GCM dan IV baru. Kolom disamarkan, dikecualikan dari autofill dan penyimpanan state, serta dikosongkan saat pengguna meninggalkan tab. Tombol Hapus dari HP menghapus salinan perangkat. Pencabutan key dilakukan melalui API Management Binance.

Uji koneksi membaca waktu Binance dan menandatangani GET ke `/fapi/v1/accountConfig`. GET opsional ke `/sapi/v1/account/apiRestrictions` memeriksa izin Futures dan penarikan pada key. API Secret dipakai di perangkat untuk HMAC. Kegagalan membaca izin ditampilkan sebagai belum terverifikasi.

Trading otomatis memakai portofolio simulasi. Menghubungkan akun hanya mengaktifkan pemeriksaan akses.

## Apa yang TIDAK BOLEH masuk repo publik

API Key/Secret, password, token autentikasi, file berisi rahasia, data
pribadi pengguna, database trading tanpa enkripsi. Lihat `.gitignore`
(mencakup `android/local.properties`, `*.apk`, `*.aab`, dan pola umum lain).

## Backup Data Sensitif

Begitu ada data trading tersimpan di perangkat (database/Room, menyusul),
backup-nya wajib terenkripsi (mis. AES-256 dengan passphrase pengguna) dan
disimpan di storage **privat**, terpisah dari repo kode publik.

## Melaporkan Kerentanan

Proyek belum punya saluran pelaporan publik (repo belum dipublikasikan).
Catat temuan keamanan langsung di `TODO.md`/issue tracker privat untuk saat
ini.

## Disclaimer Risiko

Tidak ada jaminan profit atau win rate tertentu. Pengguna bertanggung jawab
penuh atas API Key, dana, dan keputusan trading miliknya sendiri.

---

## Referensi historis (Node.js, backend/, tidak dipakai lagi)

Sebelum pivot, `backend/src/core/config/index.js` punya mekanisme serupa:
`BOT_MODE=live` ditolak di level kode, dan `describeConfigForLog()` secara
sengaja tidak pernah menyertakan nilai API Key/Secret mentah saat logging.
Pola desain ini tetap jadi referensi yang baik untuk versi Kotlin-nya nanti,
tapi kode-nya sendiri sudah tidak berjalan sebagai bagian aplikasi.
