# android/

**FASE 10 — Aplikasi Android Mandiri (tanpa Termux).** Proyek Kotlin native,
dibangun Gradle. Sejak 2026-11-07, arsitektur proyek berubah total: logic bot
yang sebelumnya direncanakan di Node.js/Termux (`../backend/`) sekarang
ditulis ulang di sini sebagai satu APK mandiri. Lihat
`../docs/PROJECT_STATUS.md` untuk alasan & detail perubahan ini.

## Status saat ini (per 2026-11-08)

**Increment 2 selesai & TERBUKTI compile+test lewat GitHub Actions** (run
42554105921): Exchange layer dasar (signing + WebSocket auto-reconnect),
dengan demo nyata di layar utama. **Belum ada**: Foreground Service (reconnect
baru hidup selama app terbuka), Boot Receiver, REST client, data layer,
penyimpanan API Key. Daftar lengkap kekurangan & rencana:
[`../docs/AUDIT_REPORT.md`](../docs/AUDIT_REPORT.md).

- `src/main/java/.../MainActivity.kt` — layar demo, konek ke stream publik Binance.
- `src/main/java/.../exchange/Signer.kt` — HMAC-SHA256 signing, 6 unit test.
- `src/main/java/.../exchange/ReconnectingWebSocket.kt` — WebSocket auto-reconnect + OkHttpClient singleton, 4 unit test (3 backoff + 1 singleton).

**Verifikasi kode**: lingkungan kerja Claude tidak punya Android SDK/Gradle/
akses jaringan, jadi SEMUA perubahan kode di folder ini baru benar-benar
terbukti jalan setelah di-push ke GitHub dan dicek di tab **Actions**
(`.github/workflows/android-build.yml`) — bukan dari klaim di commit message.

## Build lokal (nanti, kalau sudah ada Android Studio/PC)

```bash
cd android
gradle assembleDebug   # build APK
gradle test             # unit test JUnit (JVM murni, tanpa emulator)
```

(Belum ada `gradlew` wrapper -- sengaja, lihat catatan di
`.github/workflows/android-build.yml`.)

## Dapatkan APK tanpa PC (lewat GitHub Actions)

```bash
gh run list -R <owner>/me-futures-ai-bot
gh run download <run-id> -n me-futures-ai-bot-debug-apk -R <owner>/me-futures-ai-bot
```

Lalu install `app-debug.apk` lewat aplikasi Files di HP (izinkan "install
unknown apps" sekali untuk app yang dipakai membukanya).

## Struktur

```
android/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/mefuturesaibot/app/
│       │   │   ├── MainActivity.kt
│       │   │   └── exchange/
│       │   │       ├── Signer.kt
│       │   │       └── ReconnectingWebSocket.kt
│       │   └── res/
│       └── test/
│           └── java/com/mefuturesaibot/app/exchange/
│               ├── SignerTest.kt
│               ├── BackoffCalculatorTest.kt
│               └── OkHttpSingletonTest.kt
├── build.gradle.kts   (root)
├── settings.gradle.kts
└── gradle.properties
```
