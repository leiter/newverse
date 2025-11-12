package com.together.newverse.data.repository

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.AuthProvider
import com.together.newverse.data.config.Platform
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.domain.repository.AuthRepository

/**
 * Factory for creating ProfileRepository instances based on feature flags.
 * Follows the same pattern as AuthRepositoryFactory for consistency.
 */
object ProfileRepositoryFactory {

    // Cached instances
    private var firebaseInstance: ProfileRepository? = null
    private var gitLiveInstance: ProfileRepository? = null

    /**
     * Get the appropriate ProfileRepository based on current feature flags.
     *
     * @param authRepository Required dependency for profile operations
     * @param forceProvider Optional parameter to override feature flags
     * @return ProfileRepository implementation based on configuration
     */
    fun getProfileRepository(
        authRepository: AuthRepository,
        forceProvider: AuthProvider? = null
    ): ProfileRepository {
        val provider = forceProvider ?: FeatureFlags.authProvider

        println("🏭 ProfileRepositoryFactory: Selecting provider: $provider")

        return when (provider) {
            AuthProvider.FIREBASE -> getFirebaseRepository()
            AuthProvider.GITLIVE -> getGitLiveRepository(authRepository)
            AuthProvider.AUTO -> getAutoRepository(authRepository)
        }
    }

    /**
     * Get Firebase Profile Repository (Android only).
     */
    private fun getFirebaseRepository(): ProfileRepository {
        if (firebaseInstance == null) {
            // TODO: Use expect/actual pattern for platform-specific loading
            // For now, create GitLive as fallback
            println("⚠️ ProfileRepositoryFactory: Firebase not available in common code, falling back to GitLive")

            // We need auth repository for GitLive, so get it from factory
            val authRepo = AuthRepositoryFactory.getAuthRepository()
            firebaseInstance = getGitLiveRepository(authRepo)
        }
        return firebaseInstance!!
    }

    /**
     * Get GitLive Profile Repository (cross-platform).
     */
    private fun getGitLiveRepository(authRepository: AuthRepository): ProfileRepository {
        if (gitLiveInstance == null) {
            gitLiveInstance = GitLiveProfileRepository(authRepository)
        }
        return gitLiveInstance!!
    }

    /**
     * Automatically select repository based on platform and configuration.
     */
    private fun getAutoRepository(authRepository: AuthRepository): ProfileRepository {
        val platform = Platform.getCurrentPlatform()

        return when (platform) {
            Platform.IOS -> {
                println("🏭 ProfileRepositoryFactory: iOS platform detected, using GitLive")
                getGitLiveRepository(authRepository)
            }
            Platform.ANDROID -> {
                // Use same provider as auth for consistency
                if (FeatureFlags.authProvider == AuthProvider.GITLIVE) {
                    println("🏭 ProfileRepositoryFactory: Using GitLive to match auth provider")
                    getGitLiveRepository(authRepository)
                } else {
                    println("🏭 ProfileRepositoryFactory: Using Firebase to match auth provider")
                    getFirebaseRepository()
                }
            }
            Platform.WEB, Platform.DESKTOP -> {
                println("🏭 ProfileRepositoryFactory: ${platform.name} platform detected, using GitLive")
                getGitLiveRepository(authRepository)
            }
        }
    }

    /**
     * Clear cached instances (useful for testing).
     */
    fun clearCache() {
        firebaseInstance = null
        gitLiveInstance = null
    }
}