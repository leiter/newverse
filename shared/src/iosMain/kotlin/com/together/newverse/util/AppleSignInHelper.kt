package com.together.newverse.util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS Apple Sign-In Helper
 *
 * This class provides the Kotlin interface for Apple Sign-In.
 * The actual Apple Sign-In flow is handled by Swift code that calls into this.
 *
 * Prerequisites (configured in iOS app):
 * 1. Sign in with Apple capability in Xcode
 * 2. Apple Sign-In entitlements
 * 3. AppleSignInHelper.swift for native flow
 *
 * Usage:
 * The Swift layer (ContentView) should:
 * 1. Import AuthenticationServices
 * 2. Use AppleSignInHelper.swift for the native flow
 * 3. Call AppleSignInHelper methods with results
 */
class AppleSignInHelper {

    /**
     * Result data from Apple Sign-In
     */
    data class AppleSignInResult(
        val idToken: String,
        val rawNonce: String,
        val fullName: String?,
        val email: String?
    )

    // Callback storage for async sign-in
    private var signInCompletion: ((Result<AppleSignInResult>) -> Unit)? = null

    /**
     * Initiates Apple Sign-In flow.
     * This sets up the completion handler that Swift code will call.
     *
     * @param completion Callback with AppleSignInResult or error
     */
    fun signIn(
        completion: (Result<AppleSignInResult>) -> Unit
    ) {
        println("Apple Sign-In (iOS): Sign-in requested from Kotlin")
        signInCompletion = completion

        // Signal to Swift that sign-in should start
        // This will be picked up by the PlatformAction handler in AppScaffold
        // which triggers the Swift-side Apple Sign-In flow
    }

    /**
     * Called from Swift when sign-in completes successfully
     *
     * @param idToken The identity token (JWT) from Apple
     * @param rawNonce The raw nonce used for verification
     * @param fullName The user's full name (may be null on subsequent sign-ins)
     * @param email The user's email (may be null on subsequent sign-ins)
     */
    fun onSignInSuccess(idToken: String, rawNonce: String, fullName: String?, email: String?) {
        println("Apple Sign-In (iOS): Sign-in success callback received")
        val result = AppleSignInResult(
            idToken = idToken,
            rawNonce = rawNonce,
            fullName = fullName,
            email = email
        )
        signInCompletion?.invoke(Result.success(result))
        signInCompletion = null
    }

    /**
     * Called from Swift when sign-in fails
     *
     * @param errorMessage The error message
     */
    fun onSignInError(errorMessage: String) {
        println("Apple Sign-In (iOS): Sign-in error callback received: $errorMessage")
        signInCompletion?.invoke(Result.failure(Exception(errorMessage)))
        signInCompletion = null
    }

    /**
     * Called from Swift when user cancels sign-in
     */
    fun onSignInCancelled() {
        println("Apple Sign-In (iOS): Sign-in cancelled")
        signInCompletion?.invoke(Result.failure(Exception("User cancelled sign-in")))
        signInCompletion = null
    }

    /**
     * Suspending version of signIn for coroutine usage
     */
    suspend fun signInSuspend(): AppleSignInResult {
        return suspendCancellableCoroutine { continuation ->
            signIn { result ->
                result.onSuccess { appleResult ->
                    continuation.resume(appleResult)
                }.onFailure { error ->
                    continuation.resumeWithException(error)
                }
            }
        }
    }

    companion object {
        // Singleton instance for Swift interop
        val shared = AppleSignInHelper()
    }
}
