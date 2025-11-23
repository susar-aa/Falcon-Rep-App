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
        // JitPack syntax for Kotlin DSL
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "FalconRep"
include(":app")