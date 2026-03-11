package com.together.newverse.util

import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Bridge object for completing Apple Sign-In with Firebase Auth.
 *
 * This object provides a way for Swift code to call into Kotlin
 * to complete the Firebase authentication after the native Apple
 * Sign-In flow completes successfully.
 *
 * Usage from Swift:
 * ```swift
 * AppleAuthBridge.shared.completeAppleSignIn(
 *     idToken: result.idToken,
 *     rawNonce: result.rawNonce,
 *     onSuccess: { userId in
 *         print("Successfully signed in: \(userId)")
 *     },
 *     onError: { error in
 *         print("Sign in failed: \(error)")
 *     }
 * )
 * ```
 */
object AppleAuthBridge : KoinComponent {

    private val authRepository: AuthRepository by inject()

    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Completes Apple Sign-In by authenticating with Firebase.
     *
     * @param idToken The Apple identity token (JWT)
     * @param rawNonce The raw nonce used during Apple Sign-In
     * @param onSuccess Callback invoked with the user ID on success
     * @param onError Callback invoked with error message on failure
     */
    fun completeAppleSignIn(
        idToken: String,
        rawNonce: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        println("AppleAuthBridge: Completing Apple Sign-In with Firebase")

        scope.launch {
            try {
                val result = authRepository.signInWithApple(idToken, rawNonce)

                result.onSuccess { userId ->
                    println("AppleAuthBridge: Firebase auth success - userId=$userId")
                    AppleSignInState.notifyAuthComplete()
                    onSuccess(userId)
                }.onFailure { error ->
                    println("AppleAuthBridge: Firebase auth failed - ${error.message}")
                    onError(error.message ?: "Unknown error during Apple Sign-In")
                }
            } catch (e: Exception) {
                println("AppleAuthBridge: Exception during Firebase auth - ${e.message}")
                onError(e.message ?: "Unknown error during Apple Sign-In")
            }
        }
    }
}
