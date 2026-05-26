package com.together.newverse.util

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun createImageLoader(context: PlatformContext): ImageLoader {
    val httpClient = HttpClient(Js)
    return ImageLoader.Builder(context)
        .components {
            add(KtorNetworkFetcherFactory(httpClient))
        }
        .crossfade(true)
        .build()
}

actual fun initializeImageLoader(context: PlatformContext) {
    SingletonImageLoader.setSafe { createImageLoader(it) }
    println("✅ Coil ImageLoader configured for web")
}
