package com.together.newverse.util

/**
 * The web build has no Sign in with Apple flow, so there is no token to revoke.
 */
actual object AppleTokenRevoker {
    actual val isSupported: Boolean = false
    actual suspend fun reauthenticateAndRevoke(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Apple token revocation is iOS only"))
}
