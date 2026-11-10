plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.scrollmeter.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scrollmeter.app"
        // Android 11+. The PRD allowed 28, but the build was requested for Android 11 and up.
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            // Use the key kept in signing/ (git-ignored) when it exists, so every build on any
            // computer can update the app already installed on the phone. Without the file,
            // Gradle falls back to this computer's own ~/.android/debug.keystore.
            val projectKey = rootProject.file("signing/debug.keystore")
            if (projectKey.exists()) {
                storeFile = projectKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sideloaded private app: release is signed with the same key as debug (see
            // signingConfigs above), so either APK can be installed over the other.
            signingConfig = signingConfigs.getByName("debug")
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
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Robolectric needs the app's resources to render the real screens on the JVM.
            isIncludeAndroidResources = true
            all {
                // Makes ScreenshotTest write PNGs to app/build/screenshots.
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "2g"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // JVM screenshots of the real Compose screens (no phone or emulator needed).
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
