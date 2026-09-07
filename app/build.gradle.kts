import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

android {
    namespace = "org.dylanjones.sleepradio"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.dylanjones.sleepradio"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // sherpa-onnx (Phase 9 TTS) ships native libs for 4 ABIs, ~5 MB
            // each. The only deploy target is an arm64 Pixel 9, so ship just
            // that and keep the APK from ballooning. Add "x86_64" if emulator
            // testing is ever needed.
            abiFilters += "arm64-v8a"
        }
    }

    androidResources {
        // ONNX models are already compressed; storing them uncompressed lets
        // sherpa-onnx mmap them instead of inflating on load.
        noCompress += "onnx"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // isShrinkResources stays OFF: with it on, resource shrinking strips
            // a font/emoji resource and all Compose text renders invisible
            // (black screen, no crash). R8 code shrinking alone already takes
            // the APK from ~42 MB to ~4.5 MB; the resource-shrink delta (~0.25
            // MB) isn't worth the fragility.
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates the Phase 9 TTS test action in the drawer.
        buildConfig = true
    }
    testOptions {
        // JVM unit tests: let android.jar stubs (android.util.Log) return
        // defaults instead of throwing "not mocked".
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // SleepRadio: lifecycle + navigation + prefs
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // SleepRadio: Media3 (Channel A — local + radio playback, media session)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // SleepRadio: coroutines
    implementation(libs.kotlinx.coroutines.android)

    // SleepRadio: Hilt (DI)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // SleepRadio: Room (persisted source-preset slots + audiobook progress)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // SleepRadio: SAF folder access for audiobooks
    implementation(libs.androidx.documentfile)

    // SleepRadio: on-device Piper/VITS TTS for Broadcast mode (Phase 9).
    // Apache-2.0, but its native lib statically links eSpeak-NG
    // (GPL-3.0-or-later) — the project is GPL-3.0-or-later for this reason.
    implementation(libs.sherpa.onnx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
