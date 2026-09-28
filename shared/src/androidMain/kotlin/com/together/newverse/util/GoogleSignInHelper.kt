package com.together.newverse.util

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

private const val TAG = "GoogleSignIn"

/**
 * Helper class for Google Sign-In integration
 *
 * Usage:
 * 1. Initialize in your Activity/Fragment
 * 2. Call signIn() when user clicks Google Sign-In button
 * 3. Handle result in your ActivityResultLauncher
 */
class GoogleSignInHelper(
    private val context: Context,
    webClientId: String
) {
    private val googleSignInClient: GoogleSignInClient

    init {
        Log.d(TAG) { "Initializing with webClientId" }
        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        Log.d(TAG) { "Creating GoogleSignInClient..." }
        googleSignInClient = GoogleSignIn.getClient(context, gso)
        Log.d(TAG) { "Initialization complete" }
    }

    /**
     * Get the sign-in intent to launch with ActivityResultLauncher
     */
    fun getSignInIntent(): Intent {
        Log.d(TAG) { "getSignInIntent: Getting sign-in intent..." }
        val intent = googleSignInClient.signInIntent
        Log.d(TAG) { "getSignInIntent: Intent created: $intent" }
        return intent
    }

    /**
     * Handle the sign-in result from the activity result
     * @param data Intent data from the activity result
     * @return Google ID token on success, null on failure
     */
    fun handleSignInResult(data: Intent?): Result<String> {
        return try {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken != null) {
                Log.d(TAG) { "Got ID token from Google Sign-In" }
                Result.success(idToken)
            } else {
                Log.e(TAG) { "ID token is null" }
                Result.failure(Exception("Failed to get ID token from Google"))
            }
        } catch (e: ApiException) {
            Log.e(TAG) { "Sign-in failed with error code: ${e.statusCode}" }

            val errorMessage = when (e.statusCode) {
                7 -> "Network error. Please check your connection"
                10 -> "Developer error. Check your Google Sign-In configuration"
                12501 -> "Sign-in was cancelled"
                else -> "Sign-in failed: ${e.message}"
            }
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Log.e(TAG) { "Unexpected error: ${e.message}" }
            Result.failure(Exception("Sign-in failed: ${e.message}"))
        }
    }

    /**
     * Sign out from Google (asynchronous)
     * This clears the cached account
     */
    fun signOut() {
        Log.d(TAG) { "signOut: Clearing cached account..." }
        googleSignInClient.signOut()
            .addOnCompleteListener {
                Log.d(TAG) { "signOut: Completed" }
            }
            .addOnFailureListener { e ->
                Log.e(TAG) { "signOut: Failed - ${e.message}" }
            }
    }

    /**
     * Synchronously clear the cached account without signing out of Google
     */
    fun clearCachedAccount() {
        Log.d(TAG) { "clearCachedAccount: Clearing cached account..." }
        try {
            // Call signOut synchronously to clear the cache
            val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (lastAccount != null) {
                Log.d(TAG) { "clearCachedAccount: Found cached account, signing out..." }
                signOut()
            } else {
                Log.d(TAG) { "clearCachedAccount: No cached account found" }
            }
        } catch (e: Exception) {
            Log.e(TAG) { "clearCachedAccount: Exception - ${e.message}" }
        }
    }

    /**
     * Revoke access (disconnect from Google)
     */
    fun revokeAccess() {
        googleSignInClient.revokeAccess()
    }

    /**
     * Check if user is already signed in with Google
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null
    }
}
