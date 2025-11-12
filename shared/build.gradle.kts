import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
    id("com.codingfeline.buildkonfig")
    kotlin("plugin.serialization")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            // Koin for Dependency Injection
            implementation("io.insert-koin:koin-core:4.0.0")
            implementation("io.insert-koin:koin-compose:4.0.0")
            implementation("io.insert-koin:koin-compose-viewmodel:4.0.0")

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

            // DateTime
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

            // Serialization for DTOs
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

            // ViewModel
            implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.0")

            // Navigation
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")

            // Coil3 for image loading (supports Android, iOS, Desktop, Web)
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")

            // GitLive Firebase SDK for cross-platform support
            // Note: GitLive provides Kotlin Multiplatform support for Firebase
            implementation("dev.gitlive:firebase-auth:2.1.0")
            implementation("dev.gitlive:firebase-common:2.1.0")
            implementation("dev.gitlive:firebase-database:2.1.0")
            implementation("dev.gitlive:firebase-storage:2.1.0")
        }

        androidMain.dependencies {
            // Android specific dependencies
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.appcompat:appcompat:1.7.0")
            implementation("androidx.core:core-ktx:1.15.0")

            // Compose UI Tooling for Previews
            implementation(compose.preview)
            implementation(compose.uiTooling)

            // Koin Android
            implementation("io.insert-koin:koin-android:4.0.0")

            // Firebase
            implementation("com.google.firebase:firebase-auth-ktx:23.0.0")
            implementation("com.google.firebase:firebase-database-ktx:21.0.0")
            implementation("com.google.firebase:firebase-storage-ktx:21.0.0")

            // Google Sign-In
            implementation("com.google.android.gms:play-services-auth:21.2.0")

            // Coroutines Play Services
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

            // Ktor HTTP client engine for Coil image loading
            implementation("io.ktor:ktor-client-okhttp:3.0.1")
        }

        iosMain.dependencies {
            // iOS specific dependencies

            // Ktor HTTP client engine for Coil image loading
            implementation("io.ktor:ktor-client-darwin:3.0.1")
        }
    }
}

android {
    namespace = "com.together.newverse.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Exclude generated files from lint checks
        ignoreWarnings = false
        abortOnError = false

        // Exclude generated source directories
        disable += setOf(
            "ObsoleteLintCustomCheck",
            "InvalidPackage"
        )
    }

    // Define product flavors to match the app module
    flavorDimensions += "userType"

    productFlavors {
        create("buy") {
            dimension = "userType"
        }

        create("sell") {
            dimension = "userType"
        }
    }
}

// Detect the flavor from gradle tasks being executed
val requestedTasks = gradle.startParameter.taskRequests.flatMap { it.args }
val isBuyFlavor = requestedTasks.any { it.contains("Buy", ignoreCase = true) }
val isSellFlavor = requestedTasks.any { it.contains("Sell", ignoreCase = true) }

val currentFlavor = when {
    isBuyFlavor -> "buy"
    isSellFlavor -> "sell"
    else -> "buy" // default to buy
}

buildkonfig {
    packageName = "com.together.newverse.shared"

    // Default configuration based on detected flavor
    defaultConfigs {
        when (currentFlavor) {
            "buy" -> {
                buildConfigField(Type.STRING, "APP_NAME", "Newverse Buy")
                buildConfigField(Type.BOOLEAN, "IS_BUY_APP", "true")
                buildConfigField(Type.BOOLEAN, "IS_SELL_APP", "false")
                buildConfigField(Type.STRING, "USER_TYPE", "buy")
            }
            "sell" -> {
                buildConfigField(Type.STRING, "APP_NAME", "Newverse Sell")
                buildConfigField(Type.BOOLEAN, "IS_BUY_APP", "false")
                buildConfigField(Type.BOOLEAN, "IS_SELL_APP", "true")
                buildConfigField(Type.STRING, "USER_TYPE", "sell")
            }
            else -> {
                buildConfigField(Type.STRING, "APP_NAME", "Newverse")
                buildConfigField(Type.BOOLEAN, "IS_BUY_APP", "false")
                buildConfigField(Type.BOOLEAN, "IS_SELL_APP", "false")
                buildConfigField(Type.STRING, "USER_TYPE", "default")
            }
        }
    }
}
