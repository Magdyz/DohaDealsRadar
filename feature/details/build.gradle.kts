plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "qa.deals.doha.feature.details"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
        buildConfig = false
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)

    implementation(projects.core.design)
    implementation(projects.core.domain)
    implementation(projects.core.data)

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.5")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Core modules
    implementation(project(":core:data"))
    implementation(project(":core:design"))

}
