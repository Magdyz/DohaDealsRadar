// File: feature/onboarding/build.gradle.kts

plugins {
    // ✅ CHANGED: Corrected aliases to use dots, matching your libs.versions.toml
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "qa.deals.onboarding"

    // Using 36 as seen in your previous build logs
    compileSdk = 36

    defaultConfig {
        minSdk = 26 // Matching your other modules
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFile("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // ✅ CHANGED: Set to Java 17, the modern standard
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        // This correctly uses your kotlin version "2.0.20"
        kotlinCompilerExtensionVersion = libs.versions.kotlin.get()
    }
}

dependencies {
    // AndroidX & Compose basics (from your TOML)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ✅ REQUIRED: These are now correctly aliased from your TOML
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ✅ REQUIRED: Depend on your core modules
    implementation(projects.core.design) // For R.drawable.onboarding_slide_X and theme
    implementation(projects.core.data)   // For DeviceIdManager

    // Testing (from your TOML)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
}