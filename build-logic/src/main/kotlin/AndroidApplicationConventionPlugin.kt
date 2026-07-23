import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import java.io.File
import java.io.FileInputStream
import java.util.Properties

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
            }

            val versionPropsFile = file("version.properties")
            val appVersionCode = if (versionPropsFile.exists()) {
                val props = Properties()
                props.load(versionPropsFile.inputStream())
                props.getProperty("VERSION_CODE", "1").toInt()
            } else {
                1
            }

            extensions.configure<ApplicationExtension> {
                namespace = "com.together.newverse.android"
                compileSdk = 35

                defaultConfig {
                    applicationId = "com.together"
                    minSdk = 23
                    targetSdk = 37
                    versionCode = appVersionCode
                    versionName = "1.0.0"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                buildFeatures {
                    compose = true
                    buildConfig = true
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                lint {
                    // Exclude generated files from lint checks
                    ignoreWarnings = false
                    abortOnError = true

                    // Exclude generated source directories
                    disable += setOf(
                        "ObsoleteLintCustomCheck",
                        "InvalidPackage"
                    )
                }

                val releaseSigningPropsFile = File("/home/mandroid/Videos/AA_FILES/bodenkunde_signing")
                val releaseSigningProps = Properties()
                if (releaseSigningPropsFile.exists()) {
                    releaseSigningProps.load(FileInputStream(releaseSigningPropsFile))
                }

                signingConfigs {
                    getByName("debug") {
                        storeFile = file("../debug.keystore")
                        storePassword = "android"
                        keyAlias = "androiddebugkey"
                        keyPassword = "android"
                    }
                    create("release") {
                        storeFile = File("/home/mandroid/Videos/AA_FILES/boden_kunde.jks")
                        storePassword = releaseSigningProps["storePassword"] as String?
                        keyAlias = releaseSigningProps["keyAlias"] as String?
                        keyPassword = releaseSigningProps["keyPassword"] as String?
                    }
                }

                buildTypes {
                    getByName("debug") {
                        signingConfig = signingConfigs.getByName("debug")
                    }
                    release {
                        signingConfig = signingConfigs.getByName("release")
                        isMinifyEnabled = true
                        isShrinkResources = true // Remove unused resources
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                }

                flavorDimensions += "userType"

                productFlavors {
                    create("buy") {
                        dimension = "userType"
                        applicationIdSuffix = ".buy"
                        versionCode = appVersionCode
                        versionNameSuffix = "-buy"
                        matchingFallbacks += "buy"
                    }

                    create("sell") {
                        dimension = "userType"
                        applicationIdSuffix = ".sell"
                        versionCode = appVersionCode
                        versionNameSuffix = "-sell"
                        matchingFallbacks += "sell"
                    }
                }
            }

            dependencies {
                add("implementation", project(":shared"))

                // Android Core
                add("implementation", "androidx.core:core-ktx:1.15.0")
                add("implementation", "androidx.appcompat:appcompat:1.7.0")
                add("implementation", "androidx.activity:activity-compose:1.9.3")

                // Compose
                add("implementation", platform("androidx.compose:compose-bom:2024.06.00"))
                add("implementation", "androidx.compose.ui:ui")
                add("implementation", "androidx.compose.material3:material3")
                add("implementation", "androidx.compose.material:material-icons-extended")
                add("implementation", "androidx.compose.ui:ui-tooling")
                add("implementation", "androidx.compose.ui:ui-tooling-preview")


                // Koin
                add("implementation", "io.insert-koin:koin-android:4.1.0")
                add("implementation", "io.insert-koin:koin-androidx-compose:4.1.0")
                add("implementation", "io.insert-koin:koin-compose-viewmodel:4.1.0")

                // Firebase
                add("implementation", platform("com.google.firebase:firebase-bom:33.1.2"))
                add("implementation", "com.google.firebase:firebase-auth-ktx")
                add("implementation", "com.google.firebase:firebase-database-ktx")
                add("implementation", "com.google.firebase:firebase-storage-ktx")
                add("implementation", "com.google.firebase:firebase-analytics-ktx")

                // Coroutines Play Services
                add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

                // WorkManager for background task scheduling
                add("implementation", "androidx.work:work-runtime-ktx:2.9.0")

                // Google Sign-In
                add("implementation", "com.google.android.gms:play-services-auth:21.2.0")

                // QR Code scanning (buyer app only)
                add("buyImplementation", "com.google.android.gms:play-services-code-scanner:16.1.0")

                // Material Components (for testing UI)
                add("implementation", "com.google.android.material:material:1.12.0")

                // ConstraintLayout (for test container layout)
                add("implementation", "androidx.constraintlayout:constraintlayout:2.1.4")

                // Testing dependencies
                add("androidTestImplementation", "androidx.test.ext:junit:1.2.1")
                add("androidTestImplementation", "androidx.test:runner:1.6.2")
                add("androidTestImplementation", "androidx.test:rules:1.6.1")
                add("androidTestImplementation", "androidx.test.espresso:espresso-core:3.6.1")
                add("androidTestImplementation", "androidx.test.espresso:espresso-idling-resource:3.6.1")
                add("androidTestImplementation", "junit:junit:4.13.2")
            }
        }
    }
}
