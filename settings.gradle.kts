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

rootProject.name = "REACT"

// Pure Kotlin rule engine of the grid version (no Android dependency, testable on any JVM).
include(":engine")
// Pure Kotlin particle simulation of version 3 (no Android dependency, testable on any JVM).
include(":sim")
// Android app: Jetpack Compose UI on top of the engine.
include(":app")
