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
            val helper = GoogleSignInHelper(context, defaultWebClientId(context))
            helper.clearCachedAccount()

            Log.d(TAG) { "Successfully initiated Google account cache clear" }
        } catch (e: Exception) {
            Log.e(TAG) { "Failed to clear Google account: ${e.message}" }
        }
    }
}

/**
 * The Google Sign-In web client id for the Firebase project this build talks to.
 *
 * The google-services plugin generates `default_web_client_id` per build type from the
 * variant's google-services.json, but into the **application** module, so it is not on
 * this module's `R`. Hence the lookup by name.
 *
 * Hardcoding the id instead pinned release builds to the development project's OAuth
 * client, and Google Sign-In failed with "Failed to record the consent".
 */
private fun defaultWebClientId(context: android.content.Context): String {
    val id = context.resources.getIdentifier(
        "default_web_client_id", "string", context.packageName
    )
    require(id != 0) {
        "default_web_client_id is missing - the google-services plugin did not run, " +
            "or this variant's google-services.json has no web OAuth client."
    }
    return context.getString(id)
}
