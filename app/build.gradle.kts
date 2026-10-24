plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.sarat03.monomode"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.sarat03.monomode"
        minSdk = 30 // Wireless debugging needs Android 11 (ADR 0003)
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true // R8: smaller, faster app
            isShrinkResources = true
            // phones are ARM; Conscrypt's x86 libraries (6 MB) only serve emulators, which run the debug build
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // shortcut: debug key so it installs over dev builds, use a real key before publishing
            signingConfig = signingConfigs.getByName("debug")
        }
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
    implementation("androidx.compose.material:material-icons-core") // core icons; the few extra ones are in AppIcons.kt
    implementation("androidx.activity:activity-compose:1.12.4")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest") // the empty activity createComposeRule hosts content in
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(kotlin("test"))
}
