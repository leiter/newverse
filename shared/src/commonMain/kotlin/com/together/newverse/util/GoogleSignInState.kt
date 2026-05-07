package com.together.newverse.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class GoogleSignInTokens(val idToken: String, val accessToken: String)

/**
 * Bridge for signaling that Google Sign-In completed with tokens.
 * Written by GoogleSignInHelper (iosMain) after GIDSignIn succeeds.
 * Read by BuyAppViewModel (buyMain) to call signInWithGoogle() and resume initialization.
 *
 * Android does not use this — BuyMainActivity handles the token directly.
 */
object GoogleSignInState {
    private val _signInCompleted = MutableSharedFlow<GoogleSignInTokens>(extraBufferCapacity = 1)
    val signInCompleted: SharedFlow<GoogleSignInTokens> = _signInCompleted.asSharedFlow()

    fun notifySignInComplete(idToken: String, accessToken: String) {
        _signInCompleted.tryEmit(GoogleSignInTokens(idToken, accessToken))
    }
}
