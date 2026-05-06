package com.together.newverse

import com.together.newverse.di.flavorAppModule
import com.together.newverse.di.iosDomainModule
import com.together.newverse.util.initializeImageLoader
import coil3.PlatformContext
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Initialize Koin for iOS
 * This is called from SwiftUI when the app starts
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    // Configure Coil ImageLoader with caching
    initializeImageLoader(PlatformContext.INSTANCE)

    startKoin {
        appDeclaration()
        // Use flavorAppModule (provided by buyMain or sellMain) 
        // and iosDomainModule (provided by iosMain)
        modules(flavorAppModule, iosDomainModule)
    }
}

// Helper for SwiftUI
fun doInitKoin() = initKoin()
