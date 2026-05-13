import org.gradle.kotlin.dsl.`kotlin-dsl`

plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "newverse.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
