package com.together.newverse.util

import coil3.ImageLoader
import coil3.PlatformContext

/**
 * Platform-specific ImageLoader configuration
 *
 * Provides caching configuration for product images:
 * - Memory cache for quick access
 * - Disk cache for offline viewing
 * - Platform-optimized settings
 */
expect fun createImageLoader(context: PlatformContext): ImageLoader

/**
 * Initialize the singleton ImageLoader with caching
 * Call this once during app initialization
 */
expect fun initializeImageLoader(context: PlatformContext)
