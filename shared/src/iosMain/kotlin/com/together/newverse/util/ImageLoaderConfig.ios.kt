package com.together.newverse.util

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS-specific ImageLoader configuration
 * Configures memory and disk caching for optimal performance
 */
@OptIn(ExperimentalForeignApi::class)
actual fun createImageLoader(context: PlatformContext): ImageLoader {
    // Get iOS caches directory
    val cacheDir = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true
    ).first() as String

    return ImageLoader.Builder(context)
        // Memory Cache - 25% of available memory
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.25)
                .build()
        }
        // Disk Cache - 100MB for product images
        .diskCache {
            DiskCache.Builder()
                .directory("$cacheDir/image_cache".toPath())
                .maxSizeBytes(100 * 1024 * 1024) // 100 MB
                .build()
        }
        // Enable crossfade animation
        .crossfade(true)
        .build()
}

/**
 * Initialize the singleton ImageLoader for iOS
 */
@OptIn(ExperimentalForeignApi::class)
actual fun initializeImageLoader(context: PlatformContext) {
    val cacheDir = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true
    ).first() as String

    SingletonImageLoader.setSafe { ctx ->
        createImageLoader(ctx)
    }

    println("✅ Coil ImageLoader configured with caching:")
    println("   - Memory cache: 25% of available memory")
    println("   - Disk cache: 100MB at $cacheDir/image_cache")
}
