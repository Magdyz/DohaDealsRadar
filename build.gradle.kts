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

// ============================================================================
// Lint: one bundled detector (NullSafeMutableLiveData) crashes on Kotlin 2.0
// sources and takes `lintVitalRelease` — and therefore every release build —
// down with it. Disabled in every Android module until AGP ships a fixed lint.
// ============================================================================
subprojects {
    plugins.withId("com.android.application") { configureLint() }
    plugins.withId("com.android.library") { configureLint() }
}

fun Project.configureLint() {
    extensions.configure<com.android.build.api.dsl.CommonExtension<*, *, *, *, *, *>>("android") {
        lint {
            disable += "NullSafeMutableLiveData"
        }
    }
}
