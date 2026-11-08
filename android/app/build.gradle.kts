plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mefuturesaibot.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mefuturesaibot.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-fase10-foundation"
    }

    buildTypes {
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

// Sengaja MINIMAL -- zero dependency eksternal di luar Kotlin stdlib bawaan plugin,
// supaya build pertama (step 0: buktikan pipeline-nya jalan) punya risiko sekecil
// mungkin. androidx.core ditambahkan nanti begitu benar-benar dibutuhkan (notification,
// dsb di langkah berikutnya), bukan dari awal.
dependencies {
}
