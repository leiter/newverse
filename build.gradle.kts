plugins {
    // Kotlin Multiplatform
    kotlin("multiplatform").version("2.3.0").apply(false)
    kotlin("plugin.compose").version("2.3.0").apply(false)
    kotlin("plugin.serialization").version("2.3.0").apply(false)

    // Android
    id("com.android.application").version("8.13.2").apply(false)
    id("com.android.library").version("8.13.2").apply(false)

    // Compose
    id("org.jetbrains.compose").version("1.9.3").apply(false)

    // Google Services
    id("com.google.gms.google-services").version("4.4.2").apply(false)

    // BuildKonfig for build configurations
    id("com.codingfeline.buildkonfig").version("0.15.2").apply(false)
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
