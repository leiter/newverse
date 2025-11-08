package com.together.newverse.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing authentication
 */
interface AuthRepository {
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
}
