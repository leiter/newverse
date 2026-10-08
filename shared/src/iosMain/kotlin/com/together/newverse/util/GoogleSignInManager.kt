package com.together.newverse.util

private const val TAG = "GoogleSignInManager"

/**
 * iOS implementation of Google Sign-In management
 */
actual object GoogleSignInManager {
    /**
     * Clear the cached Google Sign-In account by signing out
     * This ensures the account picker appears fresh on next sign-in attempt
     */
    actual fun clearCachedAccount() {
        try {
            Log.d(TAG) { "[GoogleSignInManager] 🗑️ Clearing cached Google account on iOS" }
            GoogleSignInHelper.shared.clearCachedAccount()
            Log.d(TAG) { "[GoogleSignInManager] ✅ Successfully initiated Google account cache clear" }
        } catch (e: Exception) {
            Log.d(TAG) { "[GoogleSignInManager] Failed to clear Google account: ${e.message}" }
        }
    }
}
