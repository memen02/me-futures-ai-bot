# AUDIT REPORT — ME FUTURES AI BOT

**Tanggal**: 2026-11-08
**Metode**: inspeksi langsung seluruh file di repo (bukan dari ingatan/dokumentasi),
dicocokkan dengan bukti nyata GitHub Actions run **42554105921** (SUKSES: `gradle
test` 9/9 lulus, `gradle assembleDebug` berhasil, APK ter-upload).

---

## A. KONDISI PROYEK SAAT INI

**Benar-benar dibuat & TERBUKTI jalan (compile+test, via CI, bukan asumsi):**
- Struktur proyek Gradle Kotlin DSL (`android/`), AGP 8.13.0, Kotlin 2.2.21, compileSdk/targetSdk 36, minSdk 26.
- `Signer.kt` — HMAC-SHA256 + query string builder. 6 JUnit test, lulus di JVM murni.
- `ReconnectingWebSocket.kt` — wrapper OkHttp WebSocket, auto-reconnect (backoff eksponensial + jitter). 3 JUnit test untuk `computeBackoffMs` (fungsi murni), lulus.
- `MainActivity.kt` — demo reconnect di layar, konek ke `btcusdt@bookTicker` publik Binance. **Belum diverifikasi ulang di device sejak 2 perbaikan bug terakhir** (compileSdk, Kotlin plugin) — compile sukses, tapi install+buka ulang belum dikonfirmasi.
- `.github/workflows/android-build.yml` — `gradle test` lalu `gradle assembleDebug`, terbukti jalan 2x berturut-turut setelah perbaikan.

**Baru berupa rencana/dokumentasi, BELUM ada implementasi sama sekali:**
- Foreground Service (reconnect baru hidup selama Activity terbuka — `onStart`/`onStop`, MATI begitu app ditutup/layar mati dalam waktu lama).
- Android Boot Receiver (`BOOT_COMPLETED`) — tidak ada, auto-start setelah HP restart belum ada sama sekali.
- REST client Binance versi Kotlin — belum ada (baru WS).
- Analisis market, indikator, signal engine, risk management, paper trading — semuanya baru di `backend/` (Node.js, **referensi saja**, tidak dijalankan).
- Penyimpanan API Key (Android Keystore/EncryptedSharedPreferences) — belum ada, karena REST client (yang butuh API Key) juga belum ada.
- Database/Room — belum ada.
- Logging terstruktur Android — belum ada (`Log.d`/`Log.e` tidak dipakai sama sekali di kode saat ini).
- Notifikasi Android — belum ada.
- Dashboard/navigasi/layar lain — baru 1 layar (`MainActivity`), tanpa navigasi.

**`backend/` (Node.js, Fase 1-2 lama)**: 55 unit test, **tetap lulus**, tapi berstatus
REFERENSI — tidak pernah dijalankan sebagai bagian dari aplikasi Android. Dipertahankan
di repo untuk field-mapping Binance API yang sudah diverifikasi (lihat `API_SPEC.md`).

---

## B. KEKURANGAN

1. **Dokumentasi basi — `docs/INSTALLATION.md` dan `docs/RESTORE_GUIDE.md` 100% masih berisi instruksi Termux**, padahal arsitektur sudah pivot total ke Android mandiri sejak 2026-11-07. Siapa pun yang ikuti dokumen ini hari ini akan dapat instruksi yang SALAH TOTAL.
2. **`docs/SECURITY.md` menjelaskan penyimpanan API Key lewat `src/core/config` (Node.js)** — itu kode referensi yang tidak dipakai lagi. Tidak ada deskripsi keamanan untuk kode Android yang sungguhan dipakai sekarang.
3. **`docs/TEST_PLAN.md` berhenti di "Fase 2"** — tidak ada bagian hasil uji Fase 10 Android sama sekali, padahal itu yang aktif dikerjakan.
4. **`android/README.md` sudah basi 2 increment**: masih bilang "Baru fondasi, MainActivity minimal, tanpa logic trading" — padahal Signer+ReconnectingWebSocket sudah ada. Diagram struktur foldernya juga tidak menyebut folder `exchange/` dan `src/test/` yang sudah ada.
5. **Resource inefisiensi nyata**: `ReconnectingWebSocket` membuat `OkHttpClient()` baru lewat default parameter setiap kali di-instantiate. `MainActivity.onStart()` membuat instance baru setiap kali Activity masuk foreground. `OkHttpClient` dirancang Square untuk dipakai sebagai singleton (menyimpan thread pool & connection pool sendiri) — kalau pengguna keluar-masuk app berulang, beberapa `OkHttpClient` menumpuk sebelum thread-nya idle-timeout. Bukan crash, tapi pemborosan RAM/thread yang tidak perlu di HP 4GB.
6. **Reconnect TIDAK bertahan di background** — ini bukan bug, tapi **gap fitur nyata** dari yang diminta user ("auto reconnect" dan "jalan 24 jam" diasumsikan termasuk saat app di-minimize). Saat ini mati begitu `onStop()` terpanggil.
7. **Tidak ada penanganan error user-facing** — kalau WS gagal konek permanen (mis. internet mati total), UI cuma menampilkan teks "Menyambung ulang..." selamanya, tidak ada batas retry atau pesan yang lebih jelas ke pengguna.
8. **`android-build.yml` tidak membersihkan/memvalidasi `local.properties`** atau variabel sensitif apa pun — saat ini belum masalah (belum ada secret yang dipakai), tapi perlu diperhatikan begitu API Key mulai masuk ke kode (Fase berikutnya).
9. **Tidak ada `.gitattributes`** untuk memastikan line-ending konsisten antar OS (Termux/Linux vs kalau nanti ada yang pakai Windows) — risiko kecil, bukan kritis.

---

## C. PRIORITAS

**KRITIS** (bisa menyesatkan pengguna lain/masa depan, perbaiki duluan):
- #1 `docs/INSTALLATION.md` dan `docs/RESTORE_GUIDE.md` isinya salah total (instruksi Termux untuk proyek yang sudah tanpa Termux).

**TINGGI** (langsung terkait fitur yang diminta eksplisit — auto-reconnect & 24 jam):
- #6 Reconnect belum bertahan di background (Foreground Service belum ada).
- Boot Receiver belum ada (auto-start setelah restart, diminta eksplisit di awal).

**SEDANG**:
- #5 OkHttpClient tidak singleton (pemborosan resource, belum fatal).
- #2, #3, #4 Dokumentasi SECURITY/TEST_PLAN/android README basi.
- #7 Tidak ada batas/pesan jelas saat reconnect gagal terus-menerus.

**RENDAH**:
- #8, #9 — belum relevan sampai API Key/CI lebih matang.

---

## D. FILE YANG HARUS DIPERBAIKI

| File | Masalah | Solusi teknis |
|---|---|---|
| `docs/INSTALLATION.md` | 100% instruksi Termux, menyesatkan | Tulis ulang total: cara dapat APK dari GitHub Actions, cara install APK di HP |
| `docs/RESTORE_GUIDE.md` | Langkah recovery masih sebut Termux | Tulis ulang: restore = clone repo, build via Actions, install APK, masukkan API Key lewat UI |
| `docs/SECURITY.md` | Menjelaskan `src/core/config` (Node, tidak dipakai) | Tambah bagian khusus Android: rencana Keystore/EncryptedSharedPreferences (belum ada kode-nya, jadi tandai "direncanakan, belum diimplementasi") |
| `docs/TEST_PLAN.md` | Berhenti di Fase 2 | Tambah bagian "Fase 10 Android" dengan hasil run 42554105921 sebagai bukti |
| `android/README.md` | Status & diagram folder basi | Update status ke Increment 2, lengkapi diagram folder |
| `android/app/src/main/java/.../exchange/ReconnectingWebSocket.kt` | `OkHttpClient()` baru tiap instance | Jadikan `OkHttpClient` singleton (mis. lewat `object` terpisah atau diteruskan dari `Application`), bukan default parameter yang membuat instance baru |
| `android/app/src/main/java/.../MainActivity.kt` | Reconnect mati saat app di-minimize | Perlu Foreground Service (file baru), MainActivity cukup bind/observe status-nya, bukan memegang koneksi sendiri |
| `android/app/src/main/AndroidManifest.xml` | Belum ada Service/Receiver | Tambah `<service>` dan `<receiver android:name=...RECEIVE_BOOT_COMPLETED>` begitu file-nya dibuat |

---

## E. ARSITEKTUR YANG DIREKOMENDASIKAN

```
MainActivity (UI)
   |  bind/observe
   v
BotForegroundService (START_STICKY, notifikasi persisten)
   |
   +-- ReconnectingWebSocket (sudah ada, dipindah kepemilikannya ke Service)
   |      -> OkHttpClient SINGLETON (baru, object terpisah)
   |
   +-- (menyusul) RestClient Kotlin -- REST Binance, pakai Signer.kt yang sudah ada
   |
   +-- (menyusul) Data layer -- symbol registry, candle store (port dari backend/src/data/)
   |
   +-- (menyusul) Local storage -- EncryptedSharedPreferences (API Key), Room (candle/trade history)

BootReceiver (BOOT_COMPLETED)
   -> start BotForegroundService
```

Prinsip: **Service yang pegang koneksi & state, Activity cuma UI** — supaya reconnect
bertahan walau MainActivity ditutup, sesuai permintaan "auto reconnect" dan
"jalan setelah restart" dari awal.

---

## F. ROADMAP PENGERJAAN

1. **(mendesak, dokumentasi saja, tanpa risiko compile)** Perbaiki `INSTALLATION.md`, `RESTORE_GUIDE.md`, `SECURITY.md`, `TEST_PLAN.md`, `android/README.md`.
2. **Singleton OkHttpClient** — refactor kecil, risiko rendah, selesaikan sebelum Service dibuat (supaya Service langsung pakai yang benar).
3. **BotForegroundService** — pindahkan `ReconnectingWebSocket` ke dalamnya, notifikasi persisten (wajib Android untuk foreground service).
4. **BootReceiver** — start Service otomatis setelah reboot.
5. **RestClient Kotlin** — port dari `backend/src/exchange/restClient.js` (referensi), pakai `Signer.kt` yang sudah ada & teruji.
6. **Data layer** — port `symbolRegistry.js`/`candleStore.js`.
7. **Local storage** — EncryptedSharedPreferences untuk API Key, baru setelah itu REST signed request bisa dipakai sungguhan.
8. Lanjut ke indikator, signal engine, risk management, paper trading — sesuai urutan di `PROJECT_MASTER.md`.

Setiap langkah: push ke GitHub, `gh run watch`, baru lanjut langkah berikutnya —
pola yang sudah terbukti bekerja (menangkap 2 bug nyata sejauh ini).

---

## G. ESTIMASI BEBAN HP

- **RAM**: saat ini sangat ringan (1 Activity, 1 WebSocket, tanpa data besar disimpan). Setelah Foreground Service + data layer, perkiraan beban masih kecil SELAMA rolling window dipakai konsisten (pelajaran dari `backend/` referensi: jangan simpan semua candle semua simbol sekaligus).
- **CPU**: minimal sekarang (cuma parsing JSON kecil per pesan WS). Akan naik signifikan begitu indikator multi-timeframe aktif — perlu throttle/batas jumlah simbol dipantau detail bersamaan (sudah direncanakan di `PROJECT_MASTER.md` sebagai pendekatan Tier-1/Tier-2).
- **Baterai**: Foreground Service + WS terus-menerus = konsumsi baterai berkelanjutan, tidak bisa dihindari untuk bot yang "selalu memantau". Battery optimization exemption (sudah dibahas sebelumnya) tetap perlu.
- **Penyimpanan**: APK debug saat ini 817 KB — masih sangat kecil, jauh dari jadi masalah.
- **Koneksi**: 1 WebSocket terus-menerus = data terus mengalir (mis. bookTicker update sangat sering). Belum ada throttling tampilan — kalau nanti dipakai untuk banyak simbol sekaligus, perlu dibatasi mana yang benar-benar di-render ke UI vs cuma diproses di background.

---

## H. KRITERIA SELESAI (per tahap roadmap F)

| Tahap | Selesai kalau... |
|---|---|
| 1. Dokumentasi | Tidak ada satu pun file `docs/*.md` yang menyebut Termux sebagai langkah yang harus dilakukan pengguna |
| 2. Singleton OkHttpClient | Build hijau di Actions + 1 instance `OkHttpClient` dipakai ulang (dicek lewat kode, bukan diklaim) |
| 3. Foreground Service | Build hijau, APK diinstal, app di-minimize, status WS di notifikasi tetap "Tersambung" setelah >5 menit |
| 4. BootReceiver | HP di-restart manual, service terbukti otomatis jalan lagi tanpa buka app (dicek lewat notifikasi muncul sendiri) |
| 5. RestClient Kotlin | Unit test JUnit untuk signing lulus (mirror `SignerTest.kt`), DAN 1 panggilan REST publik (`/fapi/v1/time`) sungguhan berhasil saat dites di device |
| 6-7. Data layer, storage | Unit test untuk parsing lulus, API Key tersimpan & terbaca ulang dari Keystore tanpa plaintext terlihat di log |

Tidak ada tahap yang dianggap "selesai" dari dokumentasi saja — semua butuh bukti
build hijau DAN (untuk yang menyentuh Android-spesifik) konfirmasi device nyata,
sama seperti pola yang sudah berjalan sejauh ini.
