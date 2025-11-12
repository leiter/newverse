package com.together.newverse.data.repository

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.AuthProvider
import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/**
 * Android-specific implementation of AuthRepository that properly handles
 * switching between Firebase and GitLive implementations.
 */
class PlatformAuthRepository : AuthRepository {

    private val actualRepository: AuthRepository by lazy {
        when (FeatureFlags.authProvider) {
            AuthProvider.FIREBASE -> {
                println("🏭 PlatformAuthRepository: Using Firebase (Android native)")
                FirebaseAuthRepository()
            }
            AuthProvider.GITLIVE -> {
                println("🏭 PlatformAuthRepository: Using GitLive (cross-platform)")
                GitLiveAuthRepository()
            }
            AuthProvider.AUTO -> {
                // For Android, default to Firebase unless explicitly testing GitLive
                if (FeatureFlags.gitLiveRolloutPercentage >= 100) {
                    println("🏭 PlatformAuthRepository: Using GitLive (100% rollout)")
                    GitLiveAuthRepository()
                } else {
                    println("🏭 PlatformAuthRepository: Using Firebase (Android default)")
                    FirebaseAuthRepository()
                }
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
}