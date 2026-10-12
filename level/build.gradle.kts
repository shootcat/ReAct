import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Levels (Leitfaden V3, Phase 3): format, goals, rating. Pure Kotlin on top of the simulation, which knows
// nothing of levels.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":sim"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
