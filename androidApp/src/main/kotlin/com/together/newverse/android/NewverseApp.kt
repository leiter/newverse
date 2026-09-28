package com.together.newverse.android

import android.app.Application
import com.google.firebase.FirebaseApp
import com.together.newverse.data.firebase.GitLiveFirebaseInit
import com.together.newverse.di.androidDomainModule
import com.together.newverse.di.flavorAppModule
import com.together.newverse.util.Log
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

        com.together.newverse.util.initDebugFlag(applicationInfo)
        com.together.newverse.util.androidLogInit()

        // Initialize feature flags BEFORE Firebase and Koin
        // This must happen first to ensure all dependencies use the correct configuration
        com.together.newverse.data.config.FeatureFlagConfig.configureForProduction()
        Log.d(TAG) { "Feature flags configured for PRODUCTION" }

        // Initialize Firebase (required for both Firebase and GitLive SDKs)
        FirebaseApp.initializeApp(this)

        // Enable Firebase persistence for offline support
        // Must be called before any database reference is created
        try {
            com.google.firebase.database.FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            Log.d(TAG) { "Firebase persistence enabled" }
        } catch (e: Exception) {
            Log.w(TAG) { "Firebase persistence already enabled or failed: ${e.message}" }
        }

        // Initialize GitLive Firebase SDK (cross-platform)
        Log.d(TAG) { "Initializing GitLive Firebase SDK" }
        GitLiveFirebaseInit.initialize()

        startKoin {
            androidLogger()
            androidContext(this@NewverseApp)
            // Load flavor-specific module (from buyMain or sellMain) and Android domain module
            modules(flavorAppModule, androidDomainModule)
        }
    }

    private companion object {
        private const val TAG = "NewverseApp"
    }
}
