plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mefuturesaibot.app"
    // Dinaikkan dari 35 -> 36 (2026-11-08): com.squareup.okhttp3:okhttp-android:5.4.0
    // mewajibkan compileSdk >= 36 (AAR metadata check). Ini TERBUKTI dari error build
    // GitHub Actions sungguhan (run 42528412688), bukan tebakan. AGP 8.13.0 mendukung
    // compileSdk sampai 36, jadi tidak perlu naikkan versi AGP juga.
    compileSdk = 36

    buildFeatures { buildConfig = true }

    defaultConfig {
        buildConfigField("boolean", "MAINNET_ORDERS", "false")
        applicationId = "com.mefuturesaibot.app"
        minSdk = 26
        targetSdk = 36
        val run = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()
        val attempt = providers.environmentVariable("GITHUB_RUN_ATTEMPT").orNull?.toIntOrNull() ?: 1
        versionCode = if (run == null) 2 else run * 100 + attempt
        versionName = "0.2.0"
        val build = if (run == null) "lokal" else "$run.$attempt"
        resValue("string", "build_identity", "Versi 0.2.0 · Build $build")
    }

    testOptions.unitTests.isIncludeAndroidResources = true
    if (System.getenv("GITHUB_ACTIONS") == "true") {
        testOptions.unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
        }
    }

    buildTypes {
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            isDebuggable = false
            buildConfigField("boolean", "MAINNET_ORDERS", "false")
            matchingFallbacks += "release"
        }
        create("real") {
            initWith(getByName("release"))
            applicationIdSuffix = ".real"
            versionNameSuffix = "-unified"
            isDebuggable = false
            buildConfigField("boolean", "MAINNET_ORDERS", "true")
            matchingFallbacks += "release"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Dependency pertama yang benar-benar ditambahkan (step 0 sengaja nol dependency).
// OkHttp dipilih untuk WebSocket karena java.net.http.WebSocket bawaan Android baru
// tersedia mulai API 34 -- di bawah itu (minSdk kita 26) tidak ada WebSocket client
// bawaan platform yang bisa dipakai tanpa library. OkHttp 5.x adalah rilis stabil
// saat ini (diverifikasi dari dokumentasi resmi square/okhttp, 2026-11-08), bekerja
// dari Android API 21+. JUnit ditambahkan supaya logic murni (Signer, backoff
// calculator) bisa diuji otomatis lewat `gradle test` di GitHub Actions -- TIDAK
// butuh emulator, jalan di JVM biasa.
dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
}
