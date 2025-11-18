package com.together.newverse.config

/**
 * iOS implementation of BuildFlavorDetector
 *
 * TODO: Implement flavor detection for iOS builds
 * For now, defaults to BUY flavor
 */
actual object BuildFlavorDetector {
    actual fun getCurrentFlavor(): AppFlavor {
        // TODO: Implement iOS flavor detection
        // Could use Info.plist configuration or build settings
        println("⚠️ iOS flavor detection not implemented, defaulting to BUY")
        return AppFlavor.BUY
    }
}
