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
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    implementation(libs.androidx.material3)

    // Debug tools
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Your modules
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":feature:feed"))
    implementation(project(":feature:post"))
    implementation(project(":feature:details"))
    implementation(project(":feature:report"))

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Networking (if needed at app level)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.datastore.preferences)

}