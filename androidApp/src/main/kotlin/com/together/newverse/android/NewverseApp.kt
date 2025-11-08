package com.together.newverse.android

import android.app.Application
import com.together.newverse.di.appModule
import com.together.newverse.di.domainModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class NewverseApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@NewverseApp)
            modules(appModule, domainModule)
        }
    }
}
