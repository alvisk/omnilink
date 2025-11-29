import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

// Load keystore properties for release signing
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// 🔑 Load API keys from secrets.properties (gitignored)
val secretsPropertiesFile = rootProject.file("secrets.properties")
val secretsProperties = Properties()
if (secretsPropertiesFile.exists()) {
    secretsProperties.load(FileInputStream(secretsPropertiesFile))
}

android {
    namespace = "com.example.omni_link"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.omni_link"
        minSdk = 34 // Required for Nothing Glyph SDK (Nothing Phone 3 ships with Android 14)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        // 🔑 API Keys from secrets.properties (BuildConfig fields)
        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"${secretsProperties.getProperty("OPENROUTER_API_KEY", "")}\""
        )

        // 🚀 PERFORMANCE: Target arm64-v8a for Nothing Phone 3 (Snapdragon 8s Gen 3)
        // This enables NEON SIMD instructions and removes unnecessary x86/arm-v8 code
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // 🔐 Signing configuration for release builds
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // 🚀 PERFORMANCE: Enable optimizations for production
            isMinifyEnabled = true  // Shrink & optimize bytecode
            isShrinkResources = true // Remove unused resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with release keystore
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Fast debug builds for development
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        buildConfig = true  // Enable BuildConfig for API keys
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.splashscreen)

    // 💫 Nothing Glyph Matrix SDK - LED matrix control for Nothing Phone 3
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // 🌵 Cactus SDK - On-device LLM inference
    implementation("com.cactuscompute:cactus:1.2.0-beta")

    // 🔍 ML Kit Text Recognition - for Circle-to-Search style OCR
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // 👁️ ML Kit Image Labeling - for on-device object detection & scene analysis
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // Room for persistent memory (Track 1: Memory Master)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // JSON parsing
    implementation(libs.gson)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core-ktx:1.15.0")
        force("androidx.core:core:1.15.0")
        // Force Kotlin 2.2.0 to match Cactus SDK requirements
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.2.0")
    }
}
