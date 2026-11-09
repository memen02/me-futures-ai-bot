// Root build file -- deklarasi plugin saja (apply false), konfigurasi sebenarnya ada di app/build.gradle.kts.
//
// CATATAN PENTING: versi AGP/Kotlin di bawah ini diverifikasi dari dokumentasi resmi
// developer.android.com pada 2026-11-07 (AGP 8.13.0 adalah rilis mayor stabil terbaru
// yang saya pilih, bukan 9.2.0 yang lebih baru lagi -- sengaja pilih yang lebih
// established supaya risiko ketidakcocokan lebih kecil). TIDAK BISA saya compile sendiri
// (lingkungan kerja saya tanpa Android SDK/Gradle/jaringan) -- lihat docs/PROJECT_STATUS.md
// dan tunggu hasil build pertama di GitHub Actions sebelum menganggap versi ini final.
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
}
