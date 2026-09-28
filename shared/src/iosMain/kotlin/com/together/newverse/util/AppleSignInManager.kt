package com.together.newverse.util

/**
 * iOS implementation of Apple Sign-In management
 */
actual object AppleSignInManager {
    /**
     * Clear any cached Apple Sign-In state
     */
    actual fun clearCachedState() {
        try {
            Log.d(TAG) { "[AppleSignInManager] 🗑️ Clearing Apple Sign-In cached state on iOS" }
            AppleSignInHelper.shared.clearCachedState()
            Log.d(TAG) { "[AppleSignInManager] ✅ Successfully cleared Apple Sign-In state" }
        } catch (e: Exception) {
            Log.d(TAG) { "[AppleSignInManager] Failed to clear Apple Sign-In state: ${e.message}" }
        }
    }
}
