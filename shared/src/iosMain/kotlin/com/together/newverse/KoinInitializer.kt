package com.together.newverse

import com.together.newverse.di.appModule
import com.together.newverse.di.iosDomainModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Initialize Koin for iOS
 * This is called from SwiftUI when the app starts
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        // Use iOS-specific domain module for Firebase implementations
        modules(appModule, iosDomainModule)
    }
}

// Helper for SwiftUI
fun doInitKoin() = initKoin()
