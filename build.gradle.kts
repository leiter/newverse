plugins {
    // Kotlin Multiplatform
    kotlin("multiplatform").version("2.0.21").apply(false)
    kotlin("plugin.compose").version("2.0.21").apply(false)

    // Android
    id("com.android.application").version("8.10.1").apply(false)
    id("com.android.library").version("8.10.1").apply(false)

    // Compose
    id("org.jetbrains.compose").version("1.7.1").apply(false)
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
