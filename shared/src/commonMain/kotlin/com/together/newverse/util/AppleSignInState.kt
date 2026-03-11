package com.together.newverse.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridge for signaling that Apple Sign-In Firebase auth completed.
 * Written by AppleAuthBridge (iosMain) after signInWithApple() succeeds.
 * Read by BuyAppViewModel (buyMain) to force-refresh the AuthFlowCoordinator state.
 */
object AppleSignInState {
    private val _authCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authCompleted: SharedFlow<Unit> = _authCompleted.asSharedFlow()

    fun notifyAuthComplete() {
        _authCompleted.tryEmit(Unit)
    }
}
