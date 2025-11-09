package com.together.newverse.util

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

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
    private val webClientId: String
) {
    private val googleSignInClient: GoogleSignInClient

    init {
        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    /**
     * Get the sign-in intent to launch with ActivityResultLauncher
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
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
                println("🔐 GoogleSignInHelper: Got ID token from Google Sign-In")
                Result.success(idToken)
            } else {
                println("❌ GoogleSignInHelper: ID token is null")
                Result.failure(Exception("Failed to get ID token from Google"))
            }
        } catch (e: ApiException) {
            println("❌ GoogleSignInHelper: Sign-in failed with error code: ${e.statusCode}")
            e.printStackTrace()

            val errorMessage = when (e.statusCode) {
                7 -> "Network error. Please check your connection"
                10 -> "Developer error. Check your Google Sign-In configuration"
                12501 -> "Sign-in was cancelled"
                else -> "Sign-in failed: ${e.message}"
            }
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            println("❌ GoogleSignInHelper: Unexpected error: ${e.message}")
            e.printStackTrace()
            Result.failure(Exception("Sign-in failed: ${e.message}"))
        }
    }

    /**
     * Sign out from Google
     */
    fun signOut() {
        googleSignInClient.signOut()
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
