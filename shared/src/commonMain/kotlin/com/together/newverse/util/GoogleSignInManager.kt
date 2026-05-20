package com.together.newverse.util

/**
 * Platform-agnostic Google Sign-In management
 * Each platform (Android, iOS) provides its own implementation
 */
expect object GoogleSignInManager {
    /**
     * Clear the cached Google Sign-In account
     * Ensures fresh account picker on next sign-in attempt
     */
    fun clearCachedAccount()
}
