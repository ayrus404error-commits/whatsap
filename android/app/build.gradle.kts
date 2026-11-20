plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.glasschat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.glasschat"
        minSdk = 29
        targetSdk = 35
        // GitHub builds number themselves, so each APK installs over the last.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
        ndk { abiFilters += "arm64-v8a" }
    }

    // Release signing comes from environment variables (set from GitHub
    // secrets in CI, or exported locally). Without them the build falls back
    // to Android's debug key, so anyone can build and install it.
    // Keep your own key safe: updates only install over an APK signed with
    // the same key. Never commit a keystore to this repository.
    val ksPath = System.getenv("GLASSCHAT_KEYSTORE")
    val hasKey = !ksPath.isNullOrBlank() && file(ksPath).exists()
    signingConfigs {
        if (hasKey) {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = System.getenv("GLASSCHAT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GLASSCHAT_KEY_ALIAS")
                keyPassword = System.getenv("GLASSCHAT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        jniLibs { useLegacyPackaging = false }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(files("libs/wa.aar"))

    val composeBom = platform("androidx.compose:compose-bom:2025.07.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.core:core-ktx:1.16.0")
    // In-app camera (photo + video) with the recent-photos strip.
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-video:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
