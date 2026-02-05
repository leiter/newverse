package com.together.newverse.data.repository

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.AuthProvider
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.AuthUserInfo
import kotlinx.coroutines.flow.Flow

/**
 * iOS-specific implementation of AuthRepository.
 *
 * Currently iOS uses GitLive exclusively for cross-platform Firebase support.
 * Native Firebase iOS SDK is available via CocoaPods, but we use GitLive
 * for code sharing and consistency.
 */
class PlatformAuthRepository : AuthRepository {

    private val actualRepository: AuthRepository by lazy {
        when (FeatureFlags.authProvider) {
            AuthProvider.FIREBASE -> {
                // iOS doesn't have native Firebase wrapper yet, fall back to GitLive
                println("🏭 PlatformAuthRepository (iOS): Firebase requested but using GitLive")
                GitLiveAuthRepository()
            }
            AuthProvider.GITLIVE -> {
                println("🏭 PlatformAuthRepository (iOS): Using GitLive (cross-platform)")
                GitLiveAuthRepository()
            }
            AuthProvider.AUTO -> {
                // iOS defaults to GitLive for cross-platform compatibility
                println("🏭 PlatformAuthRepository (iOS): Using GitLive (iOS default)")
                GitLiveAuthRepository()
            }
        }
    }

    override suspend fun checkPersistedAuth(): Result<String?> {
        return actualRepository.checkPersistedAuth()
    }

    override fun observeAuthState(): Flow<String?> {
        return actualRepository.observeAuthState()
    }

    override suspend fun getCurrentUserId(): String? {
        return actualRepository.getCurrentUserId()
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<String> {
        return actualRepository.signInWithEmail(email, password)
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<String> {
        return actualRepository.signUpWithEmail(email, password)
    }

    override suspend fun signOut(): Result<Unit> {
        return actualRepository.signOut()
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return actualRepository.deleteAccount()
    }

    override suspend fun signInAnonymously(): Result<String> {
        return actualRepository.signInAnonymously()
    }

    override suspend fun isAnonymous(): Boolean {
        return actualRepository.isAnonymous()
    }

    override suspend fun signInWithGoogle(idToken: String): Result<String> {
        return actualRepository.signInWithGoogle(idToken)
    }

    override suspend fun signInWithTwitter(token: String, secret: String): Result<String> {
        return actualRepository.signInWithTwitter(token, secret)
    }

    override suspend fun signInWithApple(idToken: String, rawNonce: String): Result<String> {
        return actualRepository.signInWithApple(idToken, rawNonce)
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return actualRepository.sendPasswordResetEmail(email)
    }

    override suspend fun linkWithEmail(email: String, password: String): Result<String> {
        return actualRepository.linkWithEmail(email, password)
    }

    override suspend fun getCurrentUserInfo(): AuthUserInfo? {
        return actualRepository.getCurrentUserInfo()
    }
}
