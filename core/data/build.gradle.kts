import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")

}

// ✅ SECURITY: Load API credentials from local.properties (not committed to git)
// This prevents hardcoded secrets in source code

val localProperties = Properties()

val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

// Helper function to get property with fallback to environment variables
// Priority: local.properties -> environment variables -> error

/** Optional property: empty string when it isn't configured yet. */
fun getPropertyOrEmpty(propertyName: String): String =
    localProperties.getProperty(propertyName)
        ?: System.getenv("ORG_GRADLE_PROJECT_$propertyName")
        ?: ""

fun getPropertyOrEnv(propertyName: String): String {
    // Try local.properties first
    val localValue = localProperties.getProperty(propertyName)
    if (localValue != null && localValue.isNotEmpty()) {
        return localValue
    }

    // Try environment variable (for CI/CD)
    val envValue = System.getenv("ORG_GRADLE_PROJECT_$propertyName")
    if (envValue != null && envValue.isNotEmpty()) {
        return envValue
    }

    // Property not found - provide helpful error message
    throw GradleException(
        """
        ❌ Missing required property: $propertyName
        Please create 'local.properties' in the project root with:
        $propertyName=your_value_here
        See 'local.properties.template' for a complete example.
        For CI/CD, set environment variable: ORG_GRADLE_PROJECT_$propertyName
        """.trimIndent()
    )
}

android {
    namespace = "eg.deals.radar.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // ✅ SECURITY IMPROVEMENT: BuildConfig fields now loaded from local.properties
        // These values are no longer hardcoded in source code and won't be committed to git
        // Original hardcoded values have been moved to local.properties for safety

        buildConfigField("String", "SUPABASE_URL", "\"${getPropertyOrEnv("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${getPropertyOrEnv("SUPABASE_ANON_KEY")}\"")

        // ✅ NEW: Storage URLs also configurable (previously hardcoded in StorageUploader.kt)

        buildConfigField("String", "SUPABASE_STORAGE_URL", "\"${getPropertyOrEnv("SUPABASE_STORAGE_URL")}\"")
        buildConfigField("String", "SUPABASE_PUBLIC_URL", "\"${getPropertyOrEnv("SUPABASE_PUBLIC_URL")}\"")

        // Google Sign-In: OAuth *Web* client id from Google Cloud (not the Android one).
        // Optional so the project still builds before it is configured.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${getPropertyOrEmpty("GOOGLE_WEB_CLIENT_ID")}\"")

        // Project base URL (https://<ref>.supabase.co) for Supabase Auth (token refresh)
        buildConfigField("String", "SUPABASE_PROJECT_URL", "\"${getPropertyOrEnv("SUPABASE_PUBLIC_URL").substringBefore("/storage")}\"")

    }

    buildFeatures {
        compose = false // no UI here
        buildConfig = true

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ✅ NEW: Core domain dependency (for DealCategory enum) (2025-11-25)
    implementation(project(":core:domain"))

    testImplementation(libs.junit)
    // Room DAO tests on the JVM (in-memory database)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Credential Manager pulls play-services-auth, which still asks for
    // androidx.fragment 1.5.7 — Play flags that as an outdated SDK.
    implementation("androidx.fragment:fragment:1.8.6")

    // 🔐 Sign in with Google (Credential Manager: system account picker)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Retrofit / OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // (We'll use DataStore later in this module)
    implementation(libs.datastore.preferences)

    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    // ✅ NEW: EXIF Interface for reading image orientation
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // ✨ NEW: Coil 3.0 for image preloading (ImagePreloader utility)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)


    // ✅ NEW: Firebase Cloud Messaging (for NotificationManager) (2025-11-25)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Kotlin Coroutines (for Firebase Tasks.await())
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
