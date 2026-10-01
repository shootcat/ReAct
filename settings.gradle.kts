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

// Pure Kotlin rule engine (no Android dependency, testable on any JVM).
include(":engine")
// Android app: Jetpack Compose UI on top of the engine.
include(":app")
