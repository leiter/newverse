package com.together.newverse.util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * iOS implementation. The work itself happens in Swift
 * (NativeAppleSignInHelper.reauthenticateAndRevoke), which the app registers
 * with [AppleRevokeBridge] at startup.
 */
actual object AppleTokenRevoker {

    actual val isSupported: Boolean
        get() = AppleRevokeBridge.hasHandler

    actual suspend fun reauthenticateAndRevoke(): Result<Unit> =
        AppleRevokeBridge.revoke()
}

/**
 * Bridge for the Swift side of Apple token revocation.
 *
 * Swift registers a handler once at startup:
 *
 * ```swift
 * AppleRevokeBridge.shared.setHandler { onSuccess, onError in
 *     appleSignInHelper.reauthenticateAndRevoke { result in
 *         switch result {
 *         case .success:            onSuccess()
 *         case .failure(let error): onError(error.localizedDescription)
 *         }
 *     }
 * }
 * ```
 *
 * The handler presents Apple's sign-in sheet, so it must run on the main
 * thread and can take as long as the user takes to authorise.
 */
object AppleRevokeBridge {

    private var handler: ((() -> Unit, (String) -> Unit) -> Unit)? = null

    val hasHandler: Boolean
        get() = handler != null

    /** Called from Swift at startup. */
    fun setHandler(handler: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit) {
        println("[AppleRevokeBridge] Handler registered")
        this.handler = handler
    }

    internal suspend fun revoke(): Result<Unit> {
        val current = handler
            ?: return Result.failure(IllegalStateException("No Apple revoke handler registered"))

        return suspendCancellableCoroutine { continuation ->
            var settled = false
            current(
                {
                    if (!settled) {
                        settled = true
                        println("[AppleRevokeBridge] ✅ Apple token revoked")
                        continuation.resume(Result.success(Unit))
                    }
                },
                { message ->
                    if (!settled) {
                        settled = true
                        println("[AppleRevokeBridge] ⚠️ Apple token revocation failed: $message")
                        continuation.resume(Result.failure(Exception(message)))
                    }
                }
            )
        }
    }
}
