package com.together.newverse.util

/**
 * Android does not offer Sign in with Apple, so there is never a token to
 * revoke here.
 */
actual object AppleTokenRevoker {
    actual val isSupported: Boolean = false
    actual suspend fun reauthenticateAndRevoke(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Apple token revocation is iOS only"))
}
