package com.together.newverse.util

actual object GoogleSignInManager {
    actual fun clearCachedAccount() {
        Log.d(TAG) { "[GoogleSignInManager] Web: Google Sign-In not supported in v1" }
    }
}
