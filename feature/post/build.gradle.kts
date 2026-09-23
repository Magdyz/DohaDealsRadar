plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "eg.deals.radar.feature.post"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    // ✅ FIX: Align Java and Kotlin JVM targets
    compileOptions {
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
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    // Activity Compose - for activity result APIs
    implementation(libs.androidx.activity.compose)


    // Core modules
    implementation(project(":core:data"))
    implementation(project(":core:design"))
    implementation("androidx.exifinterface:exifinterface:1.3.3")
    implementation(libs.androidx.compose.ui.graphics)
    // ✅ This one contains ALL Material Icons
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation(projects.core.domain)  // ✅ Must be present
}