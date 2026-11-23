# SECURITY.md

> Ditulis ulang 2026-11-08 untuk arsitektur Android mandiri (tanpa Termux/
> backend terpisah). Bagian lama yang menjelaskan `src/core/config` (Node.js)
> dipindah ke bawah sebagai catatan referensi -- itu bukan kode yang dipakai
> aplikasi sekarang.

## Kredensial Binance di Android

Pengaturan menyimpan pasangan HMAC milik pengguna dalam satu berkas privat di `noBackupFilesDir`. Android Keystore menyimpan kunci AES-256. Setiap penyimpanan mengenkripsi ulang pasangan dengan GCM dan IV baru. Kolom disamarkan, dikecualikan dari autofill dan penyimpanan state, serta dikosongkan saat pengguna meninggalkan tab. Tombol Hapus dari HP menghapus salinan perangkat. Pencabutan key dilakukan melalui API Management Binance.

Uji koneksi membaca waktu Binance dan menandatangani GET ke `/fapi/v1/accountConfig`. GET opsional ke `/sapi/v1/account/apiRestrictions` memeriksa izin Futures dan penarikan pada key. API Secret dipakai di perangkat untuk HMAC. Kegagalan membaca izin ditampilkan sebagai belum terverifikasi.

Menyimpan key atau mengetuk Uji koneksi hanya memeriksa akses. Dalam mode Mainnet di Pengaturan,
mengaktifkan eksekusi memerlukan pemeriksaan akun One-way, alokasi USDT dan
persetujuan terpisah di HP. Persetujuan terikat pada identitas API key.
Gunakan akun Futures khusus untuk bot agar transaksi manual tidak bercampur
dengan order dan perhitungan hasil strategi.

Sesudah entry, aplikasi mengirim stop dan target ke bursa. Koneksi yang putus
sebelum Binance menerima stop dapat membuat posisi terbuka tanpa proteksi.
Periksa posisi dan order langsung di Binance jika HP kehilangan koneksi pada
tahap itu. Ketika terhubung kembali, aplikasi memeriksa order yang dipersistenkan
sebelum mengirim entry baru. Stop bot meminta penutupan dengan reduceOnly.
Slippage, likuidasi dan kegagalan eksekusi dapat menimbulkan kerugian yang
melampaui risiko terencana. Hasil simulasi lokal berada pada kartu terpisah.

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
