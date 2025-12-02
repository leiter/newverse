plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.together.newverse.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.together"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
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

    kotlinOptions {
        jvmTarget = "17"
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

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
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
            versionCode = 1
            versionNameSuffix = "-buy"
        }

        create("sell") {
            dimension = "userType"
            applicationIdSuffix = ".sell"
            versionCode = 1
            versionNameSuffix = "-sell"
        }
    }
}

dependencies {
    implementation(project(":shared"))

    // Android Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(compose.uiTooling)
    implementation(compose.preview)

    // Koin
    implementation("io.insert-koin:koin-android:4.0.0")
    implementation("io.insert-koin:koin-androidx-compose:4.0.0")
    implementation("io.insert-koin:koin-compose-viewmodel:4.0.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // Coroutines Play Services
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // WorkManager for background task scheduling
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Material Components (for testing UI)
    implementation("com.google.android.material:material:1.12.0")

    // ConstraintLayout (for test container layout)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Testing dependencies
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-idling-resource:3.6.1")
    androidTestImplementation("junit:junit:4.13.2")
}
