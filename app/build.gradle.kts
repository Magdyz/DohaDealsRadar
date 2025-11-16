plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "qa.deals.doha"
    compileSdk = 36

    defaultConfig {
        applicationId = "qa.deals.doha"
        minSdk = 26
        targetSdk = 36
        versionCode = 18 // Added price for post and view
        versionName = "1.1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ✅ FIXED: buildFeatures - only for enabling features
    buildFeatures {
        compose = true
        buildConfig = true  // Enable BuildConfig for production
    }

    // ✅ FIXED: buildTypes - for ProGuard and optimization
    buildTypes {
        release {
            isMinifyEnabled = true           // Enable ProGuard
            isShrinkResources = true         // Remove unused resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Production-specific settings
            isDebuggable = false

            // ✅ TODO: Add signing config when keystore is ready
            // signingConfig = signingConfigs.getByName("release")
        }

        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    kotlinOptions {
        jvmTarget = "17"
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
    // Compose BOM ensures all versions stay consistent
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))

    // Core Compose UI and Material3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Android integration
    implementation("androidx.activity:activity-compose")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    // Debug tools
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Your modules
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":feature:feed"))
    implementation(project(":feature:post"))
    implementation(project(":feature:details"))
    implementation(project(":feature:report"))
    implementation(project(":feature:onboarding"))

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Networking (if needed at app level)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.datastore.preferences)

    // ✨ NEW: BASELINE PROFILES (Performance)
    // Enables Profile-Guided Optimization (PGO) for faster app startup

    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    implementation(platform(libs.coil.bom))
    implementation(libs.coil.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // This library contains HorizontalPager
    implementation("androidx.compose.foundation:foundation:1.7.0") // Or any other recent version
}