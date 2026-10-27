package com.together.newverse.test

import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.AuthUserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake implementation of AuthRepository for testing.
 * Allows controlling auth state and responses programmatically.
 */
class FakeAuthRepository : AuthRepository {

    private val _currentUserId = MutableStateFlow<String?>(null)
    private var persistedUserId: String? = null
    private var hasCheckedPersistedAuth = false

    // Configuration for test scenarios
    var shouldFailCheckPersistedAuth = false
    var shouldFailSignIn = false
    var shouldFailSignUp = false
    var shouldFailSignOut = false
    // Separate switches: a password change fails differently depending on which
    // half of it failed, and the UI puts the two errors under different fields.
    var shouldFailReauthenticate = false
    var shouldFailUpdatePassword = false
    var failureMessage = "Test error"

    // Track method calls for verification
    var signOutCalled = false
        private set
    var lastSignInEmail: String? = null
        private set
    var lastSignInPassword: String? = null
        private set
    var reauthenticateCalled = false
        private set
    var updatePasswordCalled = false
        private set
    var lastReauthenticatePassword: String? = null
        private set
    var lastUpdatedPassword: String? = null
        private set

    /**
     * Set the current user ID directly for testing
     */
    fun setCurrentUserId(userId: String?) {
        _currentUserId.value = userId
        persistedUserId = userId
    }

    /**
     * Reset the repository state for a fresh test
     */
    fun reset() {
        _currentUserId.value = null
        persistedUserId = null
        hasCheckedPersistedAuth = false
        shouldFailCheckPersistedAuth = false
        shouldFailSignIn = false
        shouldFailSignUp = false
        shouldFailSignOut = false
        shouldFailReauthenticate = false
        shouldFailUpdatePassword = false
        failureMessage = "Test error"
        signOutCalled = false
        lastSignInEmail = null
        lastSignInPassword = null
        reauthenticateCalled = false
        updatePasswordCalled = false
        lastReauthenticatePassword = null
        lastUpdatedPassword = null
    }

    override suspend fun checkPersistedAuth(): Result<String?> {
        if (shouldFailCheckPersistedAuth) {
            return Result.failure(Exception(failureMessage))
        }
        hasCheckedPersistedAuth = true
        _currentUserId.value = persistedUserId
        return Result.success(persistedUserId)
    }

    override fun observeAuthState(): Flow<String?> {
        return _currentUserId.asStateFlow()
    }

    override suspend fun getCurrentUserId(): String? {
        return _currentUserId.value
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<String> {
        lastSignInEmail = email
        lastSignInPassword = password

        if (shouldFailSignIn) {
            return Result.failure(Exception(failureMessage))
        }

        val userId = "user_${email.hashCode()}"
        _currentUserId.value = userId
        persistedUserId = userId
        return Result.success(userId)
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<String> {
        if (shouldFailSignUp) {
            return Result.failure(Exception(failureMessage))
        }

        val userId = "new_user_${email.hashCode()}"
        _currentUserId.value = userId
        persistedUserId = userId
        return Result.success(userId)
    }

    override suspend fun signOut(): Result<Unit> {
        signOutCalled = true
        if (shouldFailSignOut) {
            return Result.failure(Exception(failureMessage))
        }

        _currentUserId.value = null
        persistedUserId = null
        return Result.success(Unit)
    }

    override suspend fun deleteAccount(): Result<Unit> {
        _currentUserId.value = null
        persistedUserId = null
        return Result.success(Unit)
    }

    override suspend fun signInAnonymously(): Result<String> {
        val userId = "guest_${System.currentTimeMillis()}"
        _currentUserId.value = userId
        return Result.success(userId)
    }

    override suspend fun isAnonymous(): Boolean {
        return _currentUserId.value?.startsWith("guest_") ?: false
    }

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?): Result<String> {
        if (shouldFailSignIn) {
            return Result.failure(Exception(failureMessage))
        }
        val userId = "google_user_${idToken.hashCode()}"
        _currentUserId.value = userId
        persistedUserId = userId
        return Result.success(userId)
    }

    override suspend fun signInWithTwitter(token: String, secret: String): Result<String> {
        if (shouldFailSignIn) {
            return Result.failure(Exception(failureMessage))
        }
        val userId = "twitter_user_${token.hashCode()}"
        _currentUserId.value = userId
        persistedUserId = userId
        return Result.success(userId)
    }

    override suspend fun signInWithApple(idToken: String, rawNonce: String): Result<String> {
        if (shouldFailSignIn) {
            return Result.failure(Exception(failureMessage))
        }
        val userId = "apple_user_${idToken.hashCode()}"
        _currentUserId.value = userId
        persistedUserId = userId
        return Result.success(userId)
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun linkWithEmail(email: String, password: String): Result<String> {
        val currentUserId = _currentUserId.value
            ?: return Result.failure(Exception("No user signed in"))
        return Result.success(currentUserId)
    }

    override suspend fun reauthenticateWithPassword(currentPassword: String): Result<Unit> {
        lastReauthenticatePassword = currentPassword
        reauthenticateCalled = true

        if (_currentUserId.value == null) {
            return Result.failure(Exception("No user signed in"))
        }
        if (shouldFailReauthenticate) {
            return Result.failure(Exception(failureMessage))
        }
        return Result.success(Unit)
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> {
        lastUpdatedPassword = newPassword
        updatePasswordCalled = true

        if (_currentUserId.value == null) {
            return Result.failure(Exception("No user signed in"))
        }
        if (shouldFailUpdatePassword) {
            return Result.failure(Exception(failureMessage))
        }
        return Result.success(Unit)
    }

    override suspend fun getCurrentUserInfo(): AuthUserInfo? {
        val userId = _currentUserId.value ?: return null
        return AuthUserInfo(
            id = userId,
            email = lastSignInEmail,
            displayName = null,
            photoUrl = null,
            isAnonymous = userId.startsWith("guest_")
        )
    }
}
