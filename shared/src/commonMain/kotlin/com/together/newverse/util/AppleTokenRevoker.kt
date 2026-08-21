package com.together.newverse.util

/**
 * Revokes the Sign in with Apple token when an account is deleted, as required
 * by App Store guideline 5.1.1(v).
 *
 * Only iOS can do this. Revocation needs an authorization code, which Apple
 * issues per authorization and expires within minutes, so it cannot be captured
 * at sign-in and stored - the sign-in has to be repeated at deletion time. On
 * iOS that repeat also clears Firebase's requires-recent-login for the delete
 * that follows.
 */
expect object AppleTokenRevoker {

    /** Whether this platform can revoke an Apple token at all. */
    val isSupported: Boolean

    /**
     * Re-authenticates with Apple and revokes the token.
     *
     * Call before deleting the account: revocation needs a signed-in user.
     * Never throws; a failure is returned so deletion can still proceed.
     */
    suspend fun reauthenticateAndRevoke(): Result<Unit>
}
