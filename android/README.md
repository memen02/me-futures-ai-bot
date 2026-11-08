# android/

**FASE 10 (dipercepat) — Fondasi Android.** Proyek Kotlin native, dibangun
Gradle, tanpa Termux. Sejak 2026-11-07, arsitektur proyek berubah: logic bot
yang sebelumnya direncanakan di Node.js/Termux (`../backend/`) sekarang
ditulis ulang di sini sebagai satu APK mandiri. Lihat
`../docs/PROJECT_STATUS.md` untuk alasan & detail perubahan ini.

## Status saat ini

Baru fondasi (`MainActivity` minimal, tanpa logic trading) — tujuannya
membuktikan pipeline build (Gradle + GitHub Actions) benar-benar jalan
sebelum logic sungguhan ditambahkan bertahap.

**PENTING**: kode di folder ini belum pernah di-compile oleh siapa pun.
Lingkungan kerja Claude tidak punya Android SDK/Gradle/akses jaringan,
jadi verifikasi PERTAMA hanya terjadi setelah di-push ke GitHub dan
GitHub Actions (`.github/workflows/android-build.yml`) menjalankan build-nya.
Cek tab **Actions** di repo GitHub Anda untuk hasilnya.

## Build lokal (nanti, kalau sudah ada Android Studio/PC)

```bash
cd android
gradle assembleDebug
```

(Belum ada `gradlew` wrapper -- sengaja, lihat catatan di
`.github/workflows/android-build.yml`.)

## Struktur

```
android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mefuturesaibot/app/MainActivity.kt
│       └── res/
├── build.gradle.kts   (root)
├── settings.gradle.kts
└── gradle.properties
```
