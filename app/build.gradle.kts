import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.google.services)  // ✅ NEW: Firebase support (2025-11-25)
    alias(libs.plugins.crashlytics)      // Crash + non-fatal error reporting
}

// ============================================================================
// Release signing
// Values come from local.properties (never committed) or environment variables,
// so the keystore password is not in the repo or in any command line.
//   RELEASE_STORE_FILE=C:/path/to/upload-keystore.jks
//   RELEASE_STORE_PASSWORD=...
//   RELEASE_KEY_ALIAS=...
//   RELEASE_KEY_PASSWORD=...
// Missing values simply leave the release build unsigned (Studio's
// "Generate Signed App Bundle" wizard still works as before).
// ============================================================================
val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(name: String): String? =
    (signingProps.getProperty(name) ?: System.getenv(name))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("RELEASE_STORE_FILE")?.let { file(it) }
val hasReleaseSigning = releaseStoreFile?.exists() == true &&
    signingValue("RELEASE_STORE_PASSWORD") != null &&
    signingValue("RELEASE_KEY_ALIAS") != null &&
    signingValue("RELEASE_KEY_PASSWORD") != null

android {
    namespace = "eg.deals.radar"
    compileSdk = 36

    defaultConfig {
        // Play Store listing package - must never change (existing listing, shipped as an update).
        // Code namespace is eg.deals.radar; the published app ID stays qa.deals.doha.
        applicationId = "qa.deals.doha"
        minSdk = 26
        targetSdk = 36
        versionCode = 26 // 26 = 2.1.0: feed tabs, account stats, duplicate check, scale tier 1, admin oversight
        versionName = "2.1.0"


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

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("RELEASE_KEY_ALIAS")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
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

            // Signed only when the keystore details are configured (see above)
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }

        debug {
            isMinifyEnabled = false
            isDebuggable = true
            // applicationIdSuffix = ".debug"  // ✅ Disabled for Firebase compatibility (2025-11-25)
            versionNameSuffix = "-DEBUG"
        }

        // Same R8 shrinking/obfuscation as release, signed with the debug key so it can be
        // installed and tested locally before uploading to Play. Never uploaded to Play.
        create("releaseTest") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-RTEST"
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
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
    // HorizontalPager lives in foundation (version managed by the Compose BOM)
    implementation(libs.androidx.compose.foundation)


    // ✅ NEW: Firebase Cloud Messaging (FCM) for push notifications (2025-11-25)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Crashlytics: crash + non-fatal error reporting (privacy-first: no user ids, no custom keys)
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // No Firebase Analytics: push notifications only (privacy-first)
}