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
        // Buy flavor source set - only for Buy builds
        val buyMain by creating {
            dependsOn(commonMain.get())
        }

        // Sell flavor source set - only for Sell builds
        val sellMain by creating {
            dependsOn(commonMain.get())
        }

        // Android flavor-specific source sets that depend on KMP source sets
        // These link the KMP source sets (buyMain/sellMain) to Android flavors
        val androidBuy by creating {
            dependsOn(androidMain.get())
            dependsOn(buyMain)
        }

        val androidSell by creating {
            dependsOn(androidMain.get())
            dependsOn(sellMain)
        }

        // Create and configure shared iOS source set
        // iOS builds include buyMain for navigation and buy-specific features
        val iosMain by creating {
            dependsOn(commonMain.get())
            dependsOn(buyMain)
        }

        // Link iOS target source sets to iosMain
        iosX64Main.get().dependsOn(iosMain)
        iosArm64Main.get().dependsOn(iosMain)
        iosSimulatorArm64Main.get().dependsOn(iosMain)

        commonMain.dependencies {
            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            // Koin for Dependency Injection
            implementation("io.insert-koin:koin-core:4.1.0")
            implementation("io.insert-koin:koin-compose:4.1.0")
            implementation("io.insert-koin:koin-compose-viewmodel:4.1.0")

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // DateTime
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

            // Serialization for DTOs
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            // ViewModel
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.6")

            // Navigation
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.1")

            // SavedState (for navigation arguments)
            implementation("org.jetbrains.androidx.savedstate:savedstate:1.3.6")

            // Coil3 for image loading (supports Android, iOS, Desktop, Web)
            implementation("io.coil-kt.coil3:coil-compose:3.3.0")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.3.0")

            // GitLive Firebase SDK for cross-platform support
            // Note: GitLive provides Kotlin Multiplatform support for Firebase
            implementation("dev.gitlive:firebase-auth:2.4.0")
            implementation("dev.gitlive:firebase-common:2.4.0")
            implementation("dev.gitlive:firebase-database:2.4.0")
            implementation("dev.gitlive:firebase-storage:2.4.0")
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
            implementation("io.insert-koin:koin-android:4.1.0")

            // Firebase
            implementation("com.google.firebase:firebase-auth-ktx:23.0.0")
            implementation("com.google.firebase:firebase-database-ktx:21.0.0")
            implementation("com.google.firebase:firebase-storage-ktx:21.0.0")

            // Google Sign-In
            implementation("com.google.android.gms:play-services-auth:21.2.0")

            // Coroutines Play Services
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

            // Ktor HTTP client engine for Coil image loading
            implementation("io.ktor:ktor-client-okhttp:3.3.0")
        }

        iosMain.dependencies {
            // iOS specific dependencies

            // Ktor HTTP client engine for Coil image loading
            implementation("io.ktor:ktor-client-darwin:3.3.0")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation("app.cash.turbine:turbine:1.2.0")
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

    // Configure flavor-specific source sets for gradual separation
    sourceSets {
        getByName("buy") {
            kotlin.srcDirs("src/buyMain/kotlin")
        }
        getByName("sell") {
            kotlin.srcDirs("src/sellMain/kotlin")
        }
    }
}

// Detect the flavor from gradle tasks being executed
val requestedTasks = gradle.startParameter.taskRequests.flatMap { it.args }
val isBuyFlavor = requestedTasks.any { it.contains("Buy", ignoreCase = true) }
val isSellFlavor = requestedTasks.any { it.contains("Sell", ignoreCase = true) }

// Determine current flavor:
// - "buy" for Buy-only builds
// - "sell" for Sell-only builds
// - "combined" for builds with both Buy and Sell features (default for development)
val currentFlavor = when {
    isBuyFlavor && !isSellFlavor -> "buy"
    isSellFlavor && !isBuyFlavor -> "sell"
    else -> "combined" // Default to combined build (both features enabled)
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
                // Combined build: both Buy and Sell features enabled
                buildConfigField(Type.STRING, "APP_NAME", "Newverse")
                buildConfigField(Type.BOOLEAN, "IS_BUY_APP", "false")
                buildConfigField(Type.BOOLEAN, "IS_SELL_APP", "false")
                buildConfigField(Type.STRING, "USER_TYPE", "combined")
            }
        }
    }
}
