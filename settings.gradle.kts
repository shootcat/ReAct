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

// Pure Kotlin particle simulation (no Android dependency, testable on any JVM).
// The grid engine of versions 1 and 2 stays at commit a7fc414 (tag grid-legacy).
include(":sim")
// Android app: Jetpack Compose UI on top of the simulation.
include(":app")
