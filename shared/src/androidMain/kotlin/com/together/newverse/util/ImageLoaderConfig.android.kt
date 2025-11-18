package com.together.newverse.util

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.util.DebugLogger

/**
 * Android-specific ImageLoader configuration
 * Configures memory and disk caching for optimal performance
 */
actual fun createImageLoader(context: PlatformContext): ImageLoader {
    val androidContext = context as Context

    return ImageLoader.Builder(context)
        // Memory Cache - 25% of available memory
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(androidContext, 0.25)
                .build()
        }
        // Disk Cache - 100MB for product images
        .diskCache {
            DiskCache.Builder()
                .directory(androidContext.cacheDir.resolve("image_cache"))
                .maxSizeBytes(100 * 1024 * 1024) // 100 MB
                .build()
        }
        // Enable crossfade animation
        .crossfade(true)
        // Enable logging in debug builds (check if app is debuggable)
        .apply {
            val isDebuggable = (androidContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                logger(DebugLogger())
            }
        }
        .build()
}

/**
 * Initialize the singleton ImageLoader for Android
 */
actual fun initializeImageLoader(context: PlatformContext) {
    val androidContext = context as Context

    SingletonImageLoader.setSafe { ctx ->
        createImageLoader(ctx)
    }

    Log.d("ImageLoaderConfig", "✅ Coil ImageLoader configured with caching:")
    Log.d("ImageLoaderConfig", "   - Memory cache: 25% of available memory")
    Log.d("ImageLoaderConfig", "   - Disk cache: 100MB at ${androidContext.cacheDir.resolve("image_cache")}")
}
