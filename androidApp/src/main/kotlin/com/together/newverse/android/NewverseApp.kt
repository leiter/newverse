package com.together.newverse.android

import android.app.Application
import com.google.firebase.FirebaseApp
import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.AuthProvider
import com.together.newverse.data.firebase.GitLiveFirebaseInit
import com.together.newverse.di.androidDomainModule
import com.together.newverse.di.flavorAppModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Application class
 *
 * Loads the flavor-specific appModule via expect/actual pattern.
 * buyMain provides BuyAppViewModel, sellMain provides SellAppViewModel.
 */
class NewverseApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize feature flags BEFORE Firebase and Koin
        // This must happen first to ensure all dependencies use the correct configuration
        com.together.newverse.data.config.FeatureFlagConfig.configureForProduction()
        println("🚀 NewverseApp: Feature flags configured for PRODUCTION (Firebase only)")

        // Initialize Firebase (required for both Firebase and GitLive SDKs)
        FirebaseApp.initializeApp(this)

        // Initialize GitLive if configured
        if (FeatureFlags.authProvider == AuthProvider.GITLIVE ||
            FeatureFlags.authProvider == AuthProvider.AUTO) {
            println("🚀 NewverseApp: Initializing GitLive Firebase SDK")
            GitLiveFirebaseInit.initialize()
        }

        startKoin {
            androidLogger()
            androidContext(this@NewverseApp)
            // Load flavor-specific module (from buyMain or sellMain) and Android domain module
            modules(flavorAppModule, androidDomainModule)
        }
    }
}
