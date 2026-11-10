# SECURITY.md

> Ditulis ulang 2026-11-08 untuk arsitektur Android mandiri (tanpa Termux/
> backend terpisah). Bagian lama yang menjelaskan `src/core/config` (Node.js)
> dipindah ke bawah sebagai catatan referensi -- itu bukan kode yang dipakai
> aplikasi sekarang.

## Status Implementasi (per 2026-11-08)

**Belum ada kode penyimpanan credential sama sekali di `android/`** -- karena
REST client (yang butuh API Key) juga belum dibangun. Bagian di bawah ini
adalah ATURAN yang akan dipakai begitu fitur itu dibangun, bukan deskripsi
kode yang sudah ada. Lihat `AUDIT_REPORT.md` dan `TODO.md` untuk status.

## Rencana Penyimpanan API Key (Kotlin/Android)

- API Key/Secret Binance **milik pengguna sendiri** — tidak pernah ada di source code, APK, atau repo publik.
- Rencana: disimpan terenkripsi di perangkat lewat `EncryptedSharedPreferences` (Jetpack Security) yang memakai Android Keystore sebagai akar enkripsinya — **belum diimplementasikan**.
- Tidak ada lagi konsep "APK ↔ backend lewat localhost" -- arsitektur sekarang satu aplikasi mandiri, jadi tidak ada komunikasi localhost yang perlu diamankan seperti rencana lama.
- Log (begitu logging Android dibangun -- saat ini belum ada) tidak boleh pernah mencatat nilai API Secret/token secara utuh.

## LIVE MODE

Prinsip "LIVE MODE terkunci sampai semua pengujian & persetujuan pengguna
selesai" (sesuai aturan awal proyek) **masih berlaku sebagai prinsip, tapi
mekanisme penguncinya di level kode BELUM diimplementasikan ulang di
Kotlin** -- versi lama (`BOT_MODE=live` diblokir di `src/core/config.js`)
adalah bagian dari backend Node.js yang sudah tidak dipakai. Ini harus
dibangun ulang begitu Execution Engine Kotlin mulai dikerjakan (lihat
Roadmap di `AUDIT_REPORT.md`) -- jangan anggap proteksi ini sudah ada hanya
karena pernah ada di versi lama.

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
