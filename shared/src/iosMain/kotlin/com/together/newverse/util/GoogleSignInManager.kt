package com.together.newverse.util

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
            println("[GoogleSignInManager] Clearing cached Google account on iOS")
            GoogleSignInHelper.shared.signOut()
            println("[GoogleSignInManager] Successfully cleared Google account")
        } catch (e: Exception) {
            println("[GoogleSignInManager] Failed to clear Google account: ${e.message}")
        }
    }
}
