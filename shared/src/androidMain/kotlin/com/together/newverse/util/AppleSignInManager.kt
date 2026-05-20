package com.together.newverse.util

/**
 * Android implementation of Apple Sign-In management
 * Apple Sign-In is iOS-only, so this is a no-op on Android
 */
actual object AppleSignInManager {
    /**
     * Clear any cached Apple Sign-In state
     * No-op on Android since Apple Sign-In is not available
     */
    actual fun clearCachedState() {
        // Apple Sign-In is not available on Android
    }
}
