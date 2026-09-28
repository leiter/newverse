package com.together.newverse.util

import android.app.Application
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "GoogleSignInMgr"

/**
 * Android implementation of Google Sign-In management
 */
actual object GoogleSignInManager : KoinComponent {
    private val application: Application by inject()

    /**
     * Clear the cached Google Sign-In account by signing out
     * This ensures the account picker appears fresh on next sign-in attempt
     */
    actual fun clearCachedAccount() {
        try {
            Log.d(TAG) { "Clearing cached Google account on Android" }
            val context = application.applicationContext

            // Create a temporary GoogleSignInHelper to access the clear method
            val webClientId = "352833414422-4qt81mifve0h0v5pu1em0tnarjmq0j7j.apps.googleusercontent.com"
            val helper = GoogleSignInHelper(context, webClientId)
            helper.clearCachedAccount()

            Log.d(TAG) { "Successfully initiated Google account cache clear" }
        } catch (e: Exception) {
            Log.e(TAG) { "Failed to clear Google account: ${e.message}" }
        }
    }
}
