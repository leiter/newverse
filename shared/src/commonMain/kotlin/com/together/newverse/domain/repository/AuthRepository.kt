package com.together.newverse.domain.repository

import kotlinx.coroutines.flow.Flow

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
    suspend fun signInWithGoogle(idToken: String): Result<String>

    /**
     * Sign in with Twitter
     * @param token Twitter OAuth token
     * @param secret Twitter OAuth secret
     * @return User ID or error
     */
    suspend fun signInWithTwitter(token: String, secret: String): Result<String>

    /**
     * Send password reset email
     * @param email User's email address
     * @return Success or error
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
}
