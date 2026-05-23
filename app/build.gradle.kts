plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.sayit.voiceime"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.sayit.voiceime"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.18.0")

    // 网络请求
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON 解析
    implementation("com.google.code.gson:gson:2.10.1")

    // 协程（异步）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}