package com.together.newverse.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Information about the authenticated user from the auth provider
 */
data class AuthUserInfo(
    val id: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean
)

/**
 * Repository for managing authentication
 */
interface AuthRepository {
    /**
     * Check if user has a persisted session and restore it
     * @return User ID if authenticated, null if not
     */
    suspend fun checkPersistedAuth(): Result<String?>

    /**
     * Observe authentication state
     * @return Flow of user ID (null if logged out)
     */
    fun observeAuthState(): Flow<String?>

    /**
     * Get current user ID
     * @return User ID or null if not authenticated
     */
    suspend fun getCurrentUserId(): String?

    /**
     * Provider ids backing the current user, e.g. "apple.com", "google.com",
     * "password". Read from the auth session; the UI's AuthProvider is only a
     * heuristic on the email address and never reports Apple.
     *
     * Empty when unknown or unsupported.
     */
    suspend fun getProviderIds(): List<String> = emptyList()

    /**
     * Sign in with email and password
     * @param email User's email
     * @param password User's password
     * @return User ID or error
     */
    suspend fun signInWithEmail(email: String, password: String): Result<String>

    /**
     * Sign up with email and password
     * @param email User's email
     * @param password User's password
     * @return User ID or error
     */
    suspend fun signUpWithEmail(email: String, password: String): Result<String>

    /**
     * Sign out current user
     */
    suspend fun signOut(): Result<Unit>

    /**
     * Delete current user account
     */
    suspend fun deleteAccount(): Result<Unit>

    /**
     * Sign in anonymously as a guest
     * @return User ID or error
     */
    suspend fun signInAnonymously(): Result<String>

    /**
     * Check if current user is anonymous/guest
     * @return true if user is anonymous, false otherwise
     */
    suspend fun isAnonymous(): Boolean

    /**
     * Sign in with Google
     * @param idToken Google ID token from Google Sign-In
     * @return User ID or error
     */
    suspend fun signInWithGoogle(idToken: String, accessToken: String? = null): Result<String>

    /**
     * Sign in with Twitter
     * @param token Twitter OAuth token
     * @param secret Twitter OAuth secret
     * @return User ID or error
     */
    suspend fun signInWithTwitter(token: String, secret: String): Result<String>

    /**
     * Sign in with Apple
     * @param idToken Apple Identity Token (JWT)
     * @param rawNonce Raw nonce for verification
     * @return User ID or error
     */
    suspend fun signInWithApple(idToken: String, rawNonce: String): Result<String>

    /**
     * Send password reset email
     * @param email User's email address
     * @return Success or error
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /**
     * Link anonymous account with email and password credentials.
     * Converts a guest account to a permanent email-authenticated account,
     * preserving all existing user data.
     * @param email User's email for the permanent account
     * @param password User's password for the permanent account
     * @return User ID or error
     */
    suspend fun linkWithEmail(email: String, password: String): Result<String>

    /**
     * Re-prove the signed-in user's identity with the password they have now.
     *
     * Firebase refuses [updatePassword] on a session older than a few minutes
     * with REQUIRES_RECENT_LOGIN, and sessions in this app are long-lived, so
     * this is the normal first half of a password change rather than a rare
     * extra step. The email is read from the session; only the password is
     * passed in.
     *
     * Kept separate from [updatePassword] so the UI can tell a wrong *current*
     * password from a rejected *new* one and put the error under the right
     * field.
     *
     * @param currentPassword The password the account has now
     * @return Success, or an error if the password is wrong, the account has no
     *   password provider, or nobody is signed in
     */
    suspend fun reauthenticateWithPassword(currentPassword: String): Result<Unit>

    /**
     * Set a new password on the signed-in account.
     *
     * Call [reauthenticateWithPassword] first; without it this fails with
     * REQUIRES_RECENT_LOGIN on anything but a freshly signed-in session.
     *
     * @param newPassword The password to set
     * @return Success or error
     */
    suspend fun updatePassword(newPassword: String): Result<Unit>

    /**
     * Get current authenticated user's info from the auth provider.
     * Returns email, display name, and photo URL if available.
     * @return User info or null if not authenticated
     */
    suspend fun getCurrentUserInfo(): AuthUserInfo?
}
