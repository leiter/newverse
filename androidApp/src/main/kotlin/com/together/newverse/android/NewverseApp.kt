package com.together.newverse.android

import android.app.Application
import com.google.firebase.FirebaseApp
import com.together.newverse.di.androidDomainModule
import com.together.newverse.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class NewverseApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        startKoin {
            androidLogger()
            androidContext(this@NewverseApp)
            // Use Android-specific domain module for Firebase implementations
            modules(appModule, androidDomainModule)
        }
    }
}
