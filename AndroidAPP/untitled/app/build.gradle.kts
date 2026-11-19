plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.neurafit.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.neurafit.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val authBaseUrl = providers.gradleProperty("AUTH_BASE_URL")
            .orElse("http://10.0.2.2:8788/api")
            .get()
        val signalingUrl = providers.gradleProperty("SIGNALING_URL")
            .orElse("ws://10.0.2.2:8787/motion-signaling")
            .get()
        val rtcIceServers = providers.gradleProperty("RTC_ICE_SERVERS")
            .orElse("""[{"urls":"stun:stun.l.google.com:19302"}]""")
            .get()
        val rtcForceRelay = providers.gradleProperty("RTC_FORCE_RELAY")
            .orElse("false")
            .get()
        val useDemoWorkoutVideo = providers.gradleProperty("USE_DEMO_WORKOUT_VIDEO")
            .orElse("false")
            .get()

        buildConfigField("String", "AUTH_BASE_URL", "\"$authBaseUrl\"")
        buildConfigField("String", "SIGNALING_URL", "\"$signalingUrl\"")
        buildConfigField("String", "RTC_ICE_SERVERS", "\"${rtcIceServers.replace("\"", "\\\"")}\"")
        buildConfigField("boolean", "RTC_FORCE_RELAY", rtcForceRelay.toBoolean().toString())
        buildConfigField("boolean", "USE_DEMO_WORKOUT_VIDEO", useDemoWorkoutVideo.toBoolean().toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    implementation("androidx.compose.ui:ui:1.11.0")
    implementation("androidx.compose.ui:ui-graphics:1.11.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.0")
    implementation("androidx.compose.foundation:foundation:1.11.0")
    implementation("androidx.compose.animation:animation:1.11.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")

    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("io.github.webrtc-sdk:android:144.7559.05")
}
