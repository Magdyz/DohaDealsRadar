plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")

}

android {
    namespace = "qa.deals.doha.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // ✅ ADD THIS - BuildConfig fields
        buildConfigField("String", "SUPABASE_URL", "\"https://nzchbnshkrkdqpcawohu.functions.supabase.co/\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56Y2hibnNoa3JrZHFwY2F3b2h1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAxODE3ODMsImV4cCI6MjA3NTc1Nzc4M30.rBl_9k6kd3ICQCD0Th8ysUu6YGozYGC12Pjl_Ra01l0\"")

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

    // (We’ll use DataStore later in this module)
    implementation(libs.datastore.preferences)

    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")  // Now this will work
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("io.coil-kt:coil-compose:2.5.0")
    // ✅ NEW: EXIF Interface for reading image orientation
    implementation("androidx.exifinterface:exifinterface:1.3.7")

}
