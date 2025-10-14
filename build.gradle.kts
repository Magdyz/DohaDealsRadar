plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    // Crashlytics & Google services will be used later (Step 12); available in catalog
    alias(libs.plugins.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
}
