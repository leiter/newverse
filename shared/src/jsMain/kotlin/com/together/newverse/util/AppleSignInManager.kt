package com.together.newverse.util

/**
 * The web build has no Sign in with Apple flow, so there is no cached state to
 * clear. Present only to satisfy the expect declaration.
 */
actual object AppleSignInManager {
    actual fun clearCachedState() {
        println("[AppleSignInManager] No Apple Sign-In on web; nothing to clear")
    }
}
