plugins {
    id("com.android.application")
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
}