package com.together.newverse.data.repository

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.AuthProvider
import com.together.newverse.data.config.Platform
import com.together.newverse.domain.repository.AuthRepository

/**
 * Factory for creating AuthRepository instances based on feature flags.
 * This enables runtime switching between Firebase and GitLive implementations.
 */
object AuthRepositoryFactory {

    // Cached instances to avoid recreating repositories
    private var firebaseInstance: AuthRepository? = null
    private var gitLiveInstance: AuthRepository? = null
    private var inMemoryInstance: AuthRepository? = null

    /**
     * Get the appropriate AuthRepository based on current feature flags.
     *
     * @param forceProvider Optional parameter to override feature flags
     * @param userId Optional user ID for rollout percentage calculations
     * @return AuthRepository implementation based on configuration
     */
    fun getAuthRepository(
        forceProvider: AuthProvider? = null,
        userId: String? = null
    ): AuthRepository {
        val provider = forceProvider ?: determineProvider(userId)

        println("🏭 AuthRepositoryFactory: Selecting provider: $provider")

        return when (provider) {
            AuthProvider.FIREBASE -> getFirebaseRepository()
            AuthProvider.GITLIVE -> getGitLiveRepository()
            AuthProvider.AUTO -> getAutoRepository(userId)
        }
    }

    /**
     * Get Firebase Auth Repository (Android only).
     * Note: This will only work on Android platform.
     */
    private fun getFirebaseRepository(): AuthRepository {
        if (firebaseInstance == null) {
            // TODO: Implement expect/actual pattern for platform-specific Firebase loading
            // For now, fallback to GitLive which is cross-platform
            println("⚠️ AuthRepositoryFactory: Firebase reflection not available in common code, falling back to GitLive")
            firebaseInstance = getGitLiveRepository()
        }
        return firebaseInstance!!
    }

    /**
     * Get GitLive Auth Repository (cross-platform).
     */
    private fun getGitLiveRepository(): AuthRepository {
        if (gitLiveInstance == null) {
            gitLiveInstance = GitLiveAuthRepository()
        }
        return gitLiveInstance!!
    }

    /**
     * Get In-Memory Auth Repository (for testing).
     */
    fun getInMemoryRepository(): AuthRepository {
        if (inMemoryInstance == null) {
            inMemoryInstance = InMemoryAuthRepository()
        }
        return inMemoryInstance!!
    }

    /**
     * Automatically select repository based on platform and configuration.
     */
    private fun getAutoRepository(userId: String?): AuthRepository {
        val platform = Platform.getCurrentPlatform()

        return when (platform) {
            Platform.IOS -> {
                // iOS doesn't support Firebase directly, use GitLive
                println("🏭 AuthRepositoryFactory: iOS platform detected, using GitLive")
                getGitLiveRepository()
            }
            Platform.ANDROID -> {
                // Android supports both, check feature flags
                if (FeatureFlags.shouldUseGitLive(userId, platform)) {
                    println("🏭 AuthRepositoryFactory: Using GitLive for user: $userId")
                    getGitLiveRepository()
                } else {
                    println("🏭 AuthRepositoryFactory: Using Firebase for user: $userId")
                    getFirebaseRepository()
                }
            }
            Platform.WEB, Platform.DESKTOP -> {
                // Web and Desktop use GitLive for Firebase support
                println("🏭 AuthRepositoryFactory: ${platform.name} platform detected, using GitLive")
                getGitLiveRepository()
            }
        }
    }

    /**
     * Determine which provider to use based on configuration.
     */
    private fun determineProvider(userId: String?): AuthProvider {
        // Check if user is in test list
        if (userId != null && FeatureFlags.gitLiveTestUsers.contains(userId)) {
            println("🏭 AuthRepositoryFactory: User $userId is in GitLive test list")
            return AuthProvider.GITLIVE
        }

        return FeatureFlags.authProvider
    }

    /**
     * Clear cached instances (useful for testing).
     */
    fun clearCache() {
        firebaseInstance = null
        gitLiveInstance = null
        inMemoryInstance = null
    }

    /**
     * Get both repositories for parallel testing.
     * This is used when enableAuthParallelTesting is true.
     */
    fun getBothRepositories(): Pair<AuthRepository, AuthRepository> {
        return Pair(getFirebaseRepository(), getGitLiveRepository())
    }
}

/**
 * Helper class for parallel testing of both auth implementations.
 * This logs and compares results from both Firebase and GitLive.
 */
class ParallelTestingAuthRepository(
    private val primary: AuthRepository,
    private val secondary: AuthRepository
) : AuthRepository {

    override suspend fun checkPersistedAuth(): Result<String?> {
        val primaryResult = primary.checkPersistedAuth()
        if (FeatureFlags.enableAuthParallelTesting) {
            val secondaryResult = secondary.checkPersistedAuth()
            logComparison("checkPersistedAuth", primaryResult, secondaryResult)
        }
        return primaryResult
    }

    override fun observeAuthState() = primary.observeAuthState()

    override suspend fun getCurrentUserId() = primary.getCurrentUserId()

    override suspend fun signInWithEmail(email: String, password: String): Result<String> {
        val primaryResult = primary.signInWithEmail(email, password)
        if (FeatureFlags.enableAuthParallelTesting) {
            val secondaryResult = secondary.signInWithEmail(email, password)
            logComparison("signInWithEmail", primaryResult, secondaryResult)
        }
        return primaryResult
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<String> {
        val primaryResult = primary.signUpWithEmail(email, password)
        if (FeatureFlags.enableAuthParallelTesting) {
            val secondaryResult = secondary.signUpWithEmail(email, password)
            logComparison("signUpWithEmail", primaryResult, secondaryResult)
        }
        return primaryResult
    }

    override suspend fun signOut() = primary.signOut()

    override suspend fun deleteAccount() = primary.deleteAccount()

    override suspend fun signInAnonymously() = primary.signInAnonymously()

    override suspend fun isAnonymous() = primary.isAnonymous()

    override suspend fun signInWithGoogle(idToken: String) = primary.signInWithGoogle(idToken)

    override suspend fun signInWithTwitter(token: String, secret: String) =
        primary.signInWithTwitter(token, secret)

    override suspend fun sendPasswordResetEmail(email: String) =
        primary.sendPasswordResetEmail(email)

    private fun logComparison(method: String, primary: Any?, secondary: Any?) {
        println("🔄 ParallelTesting[$method]: Primary=$primary, Secondary=$secondary, Match=${primary == secondary}")
    }
}