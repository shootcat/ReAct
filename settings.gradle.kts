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
// Levels: format, goals, rating, on top of the simulation (which knows nothing of levels).
include(":level")
// Android app: Jetpack Compose UI on top of the simulation.
include(":app")
