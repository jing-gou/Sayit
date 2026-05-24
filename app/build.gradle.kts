plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

android {
    namespace = "org.sayit.voiceime"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.sayit.voiceime"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 从 local.properties 读取 ASR 配置（火山引擎新版本控制台）
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        val asrApiKey = localProperties.getProperty("asr.api_key") ?: ""
        val asrResourceId = localProperties.getProperty("asr.resource_id") ?: ""
        val asrWsUrl = localProperties.getProperty("asr.ws_url") ?: ""

        val llmApiKey = localProperties.getProperty("llm.api_key") ?: ""
        val llmApiUrl = localProperties.getProperty("llm.api_url") ?: ""
        val llmModel = localProperties.getProperty("llm.model") ?: "doubao"

        buildConfigField("String", "ASR_API_KEY", "\"$asrApiKey\"")
        buildConfigField("String", "ASR_RESOURCE_ID", "\"$asrResourceId\"")
        buildConfigField("String", "ASR_WS_URL", "\"$asrWsUrl\"")
        buildConfigField("String", "LLM_API_KEY", "\"$llmApiKey\"")
        buildConfigField("String", "LLM_API_URL", "\"$llmApiUrl\"")
        buildConfigField("String", "LLM_MODEL", "\"$llmModel\"")
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

    // 动画（弹性吸附）
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")

    // SSE 流式响应
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // Material Design
    implementation("com.google.android.material:material:1.11.0")
}