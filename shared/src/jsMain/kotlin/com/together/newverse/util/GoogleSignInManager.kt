package com.together.newverse.util

actual object GoogleSignInManager {
    actual fun clearCachedAccount() {
        println("[GoogleSignInManager] Web: Google Sign-In not supported in v1")
    }
}
