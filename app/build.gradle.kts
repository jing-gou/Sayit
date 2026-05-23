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

        // 从 local.properties 读取 ASR 配置
        val asrAppId = project.findProperty("asr.app_id") as? String ?: ""
        val asrKeyId = project.findProperty("asr.access_key_id") as? String ?: ""
        val asrKeySecret = project.findProperty("asr.access_key_secret") as? String ?: ""
        val asrWsUrl = project.findProperty("asr.ws_url") as? String ?: ""

        buildConfigField("String", "ASR_APP_ID", "\"$asrAppId\"")
        buildConfigField("String", "ASR_ACCESS_KEY_ID", "\"$asrKeyId\"")
        buildConfigField("String", "ASR_ACCESS_KEY_SECRET", "\"$asrKeySecret\"")
        buildConfigField("String", "ASR_WS_URL", "\"$asrWsUrl\"")
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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")

    // 网络请求
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON 解析
    implementation("com.google.code.gson:gson:2.10.1")

    // 协程（异步）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}