plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.sarat03.phonewarden"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.sarat03.phonewarden"
        minSdk = 30 // Wireless debugging needs Android 11 (ADR 0003)
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }
    // debloat lists bundled in the app, never downloaded (ADR 0002)
    sourceSets["main"].assets.srcDir("../examples")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":adb"))
    implementation(project(":engine"))
    // TLS 1.3 with key export on Android 11; the OS API only arrives in Android 12 (ADR 0001).
    implementation("org.conscrypt:conscrypt-android:2.7.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(kotlin("test"))
}
