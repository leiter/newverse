package com.together.newverse

import coil3.PlatformContext
import com.together.newverse.di.flavorAppModule
import com.together.newverse.di.webDomainModule
import com.together.newverse.util.initializeImageLoader
import org.koin.core.context.startKoin

fun initKoin() {
    initializeImageLoader(PlatformContext.INSTANCE)
    startKoin {
        modules(flavorAppModule, webDomainModule)
    }
}
