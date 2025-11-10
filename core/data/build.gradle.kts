plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")

}

// ✅ SECURITY: Load API credentials from local.properties (not committed to git)
// This prevents hardcoded secrets in source code

val localProperties = java.util.Properties()

val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

// Helper function to get property with fallback to environment variables
// Priority: local.properties -> environment variables -> error

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
    namespace = "qa.deals.doha.core.data"
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
    // Retrofit / OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // (We'll use DataStore later in this module)
    implementation(libs.datastore.preferences)

    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")  // Now this will work
    implementation("androidx.room:room-ktx:2.6.1")
    // ✅ NEW: EXIF Interface for reading image orientation
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // ✨ NEW: Coil 3.0 for image preloading (ImagePreloader utility)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
