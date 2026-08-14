plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.omnitalk"
    compileSdk = 34

    // MUST match what is actually installed. AGP 8.7.3 otherwise defaults to
    // 27.0.12077973 and CMake dies with a missing toolchain file.
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "dev.omnitalk"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        // arm64 only. This is an Arm on-device project; shipping x86 would be
        // dead weight and we have never tested it.
        ndk { abiFilters += "arm64-v8a" }

        externalNativeBuild {
            cmake { arguments += listOf("-DANDROID_STL=c++_shared") }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../../native/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")   // judges install the APK directly
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Android's own PdfRenderer only rasterises pages — it exposes no text layer,
    // so it cannot support search or retrieval. PdfBox-Android does, offline.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
