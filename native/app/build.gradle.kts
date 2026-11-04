// Quiet, the native Android app (Kotlin + Jetpack Compose). See docs/plan-native.md.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

// The layer being built. The switch (end of L8) drops it and moves to app.quietreader.
val quietVersion = "0.5.0"
val quietLayer = "L8"

android {
    namespace = "app.quietreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.quietreader.n"
        minSdk = 26
        targetSdk = 36
        versionCode = 516
        versionName = "$quietVersion $quietLayer"
        buildConfigField("String", "QUIET_VERSION", "\"$quietVersion\"")
        buildConfigField("String", "QUIET_LAYER", "\"$quietLayer\"")
    }

    signingConfigs {
        // The same test key as the Capacitor app, so the switch can install over 0.4.2.
        create("test") {
            storeFile = file("../../android/app/quiet-test.keystore")
            storePassword = "quiet-test"
            keyAlias = "quiet-test"
            keyPassword = "quiet-test"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("test") }
        release {
            // Optimized: Compose runs much smoother in a release build than in a debug one.
            signingConfig = signingConfigs.getByName("test")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // Readium needs it
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric downloads Android jars from Maven Central, which rate-limits cloud
                // machines; Google's mirror of it doesn't.
                it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
                it.systemProperty("robolectric.dependency.repo.id", "central-mirror")
                it.maxHeapSize = "3g"
            }
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    // L7, every format: HTML cleaning, Markdown, and comic archives (7z, tar, RAR4).
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    implementation("com.github.junrar:junrar:7.5.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Readium (BSD-3): opens EPUB and draws its pages. See docs/plan-native.md.
    val readium = "3.1.2"
    implementation("org.readium.kotlin-toolkit:readium-shared:$readium")
    implementation("org.readium.kotlin-toolkit:readium-streamer:$readium")
    implementation("org.readium.kotlin-toolkit:readium-navigator:$readium")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.50.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.50.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.50.0")
}

dependencies {
    testImplementation("androidx.test:core:1.7.0")
}
