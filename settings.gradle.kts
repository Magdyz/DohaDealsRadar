pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "DohaDealsRadar"

include(":app")
include(":core:design")
include(":core:domain")
include(":core:data")
include(":feature:feed")
include(":feature:details")
include(":feature:post")
include(":feature:report")
include(":core_domain")
include(":feature:onboarding")
