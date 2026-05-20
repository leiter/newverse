package com.together.newverse.util

/**
 * Platform-agnostic Apple Sign-In management
 * Each platform (Android, iOS) provides its own implementation
 */
expect object AppleSignInManager {
    /**
     * Clear any cached Apple Sign-In state
     */
    fun clearCachedState()
}
