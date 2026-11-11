# PROJECT STATUS

**Fase saat ini:** FASE 10 Android -- Foreground Service ditambahkan (Roadmap F langkah 3). BELUM dites di device nyata -- CI hijau belum cukup untuk fitur ini, lihat CHANGELOG.md. Boot Receiver (langkah 4) menyusul.
**Terakhir diperbarui:** 2026-11-07

## PERUBAHAN ARSITEKTUR BESAR (2026-11-07)

Atas permintaan eksplisit: proyek berubah dari "backend Node.js di Termux +
APK monitor terpisah" menjadi **satu aplikasi Android Kotlin mandiri**
(tanpa Termux sama sekali untuk menjalankan bot). Keputusan yang dikonfirmasi:

- **Pendekatan**: Kotlin murni ditulis ulang, BUKAN membungkus Node.js lama
  (sempat dipertimbangkan lewat `nodejs-mobile`, tapi integrasinya butuh
  NDK/JNI -- kompleksitas tambahan yang tidak sepadan).
- **Kode `backend/` (Fase 1-2, Node.js, 55 unit test)** tetap ada di repo
  sebagai **referensi desain** (keputusan arsitektur, field mapping Binance
  API yang sudah diverifikasi, dll) -- TIDAK dihapus, TAPI tidak lagi jadi
  kode yang dijalankan.
- **GitHub repo** jadi prasyarat wajib (sebelumnya cuma "disarankan") --
  karena ini **satu-satunya cara memverifikasi kode Kotlin benar-benar
  compile**, lihat bagian "Keterbatasan Verifikasi" di bawah.

## Keterbatasan Verifikasi -- PENTING, baca ini

Lingkungan kerja Claude TIDAK punya Android SDK, Gradle, Kotlin compiler,
atau akses jaringan. Ini beda total dari `backend/` Node.js yang semua
kodenya benar-benar dijalankan & diuji (55/55 test lulus) sebelum dikirim.

**Kode Kotlin di `android/` BELUM PERNAH di-compile oleh siapa pun.**
Verifikasi pertama HANYA terjadi lewat GitHub Actions
(`.github/workflows/android-build.yml`) setelah di-push. Kalau build gagal
dengan error versi/dependency, itu wajar untuk percobaan pertama -- bukan
tanda ada yang fatal salah, tinggal diperbaiki berdasarkan pesan error
sungguhan (yang baru bisa saya lihat setelah Anda tempel di sini).

## Yang Sudah Dibangun (android/, Fase 10 fondasi)

- Struktur proyek Gradle (Kotlin DSL): `settings.gradle.kts`, root
  `build.gradle.kts`, `app/build.gradle.kts`.
- `MainActivity.kt` minimal -- layout dibuat programatik (bukan XML
  terpisah), nol dependency eksternal selain Kotlin stdlib, supaya
  percobaan compile pertama punya risiko sekecil mungkin.
- Icon placeholder (vector drawable, tidak perlu file gambar biner).
- `.github/workflows/android-build.yml` -- build APK debug via
  `gradle/actions/setup-gradle` (TIDAK pakai `./gradlew`, karena
  `gradle-wrapper.jar` adalah file biner yang tidak bisa saya buat tanpa
  Gradle+jaringan).

Versi yang dipakai (diverifikasi dari dokumentasi resmi developer.android.com
per 2026-11-07, TAPI belum pernah benar-benar dicoba compile):
AGP 8.13.0, Kotlin 2.0.21, Gradle 8.13, JDK 17, compileSdk/targetSdk 35,
minSdk 26.

## WAJIB Dilakukan Sebelum Lanjut

1. Ikuti 7 langkah setup GitHub yang sudah diberikan (akun -> repo privat
   `me-futures-ai-bot` -> Personal Access Token -> git di Termux -> push).
2. Push isi repo ini (hasil ekstrak zip) ke GitHub.
3. Buka tab **Actions** di github.com, lihat hasil build **android-build.yml**.
4. **Tempel hasilnya di sini** -- hijau (sukses) atau merah + pesan error
   (gagal). Ini yang menentukan langkah berikutnya: kalau sukses, saya mulai
   bangun logic sungguhan (networking, data layer, dst, satu per satu,
   masing-masing di-push & dicek Actions-nya); kalau gagal, saya perbaiki
   versi/konfigurasi berdasarkan error aslinya.

## Yang TIDAK berubah

- Aturan trading, risk management, keamanan API Key, dan fitur-fitur
  rekomendasi yang sudah disetujui (lihat `PROJECT_MASTER.md` §Fitur
  Tambahan Direkomendasikan) -- semua tetap berlaku, cuma bahasa
  implementasinya yang berubah dari Node.js ke Kotlin.
- Temuan API Binance (migrasi Algo Order untuk TP/SL, split WS
  `/public`/`/market`/`/private`) -- tetap relevan, akan diimplementasikan
  ulang di Kotlin dengan prinsip yang sama (lihat `API_SPEC.md`).
- "24 jam = best-effort" -- sekarang malah lebih baik karena Foreground
  Service Android native lebih reliable daripada proses Termux, tapi tetap
  bukan jaminan mutlak (batasan OS Android, bukan soal bahasa pemrograman).

## Berikutnya

Setelah build pertama di GitHub Actions sukses (langkah wajib di atas),
lanjut bangun secara bertahap: lapisan jaringan (REST+WS, versi Kotlin dari
`backend/src/exchange/`), lalu data layer, dst -- mengikuti filosofi yang
sama seperti Fase 1-2 (kecil-kecil, diuji tiap langkah, tidak sekaligus
ribuan baris), hanya medianya yang beda: JUnit test lokal untuk logic murni
(signer, parsing) + verifikasi device sungguhan untuk bagian Android-spesifik
(Foreground Service, auto-reconnect, auto-start setelah reboot).
