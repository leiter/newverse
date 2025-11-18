package com.together.newverse.data.config

/**
 * Feature flags for controlling application behavior and gradual feature rollout.
 * These flags enable A/B testing and safe migration between different implementations.
 */
object FeatureFlags {

    /**
     * Authentication Provider Selection
     * Controls which authentication implementation is used throughout the app.
     *
     * Options:
     * - FIREBASE: Use the existing FirebaseAuthRepository (Android only)
     * - GITLIVE: Use the new GitLiveAuthRepository (cross-platform)
     * - AUTO: Automatically select based on platform (GitLive for iOS, Firebase for Android)
     *
     * Default: FIREBASE (to maintain existing behavior during migration)
     *
     * Migration strategy:
     * 1. Start with FIREBASE to ensure no breaking changes
     * 2. Test with specific users/builds using GITLIVE
     * 3. Use AUTO for platform-specific optimization
     * 4. Eventually switch to GITLIVE as default once stable
     */
    var authProvider: AuthProvider = AuthProvider.FIREBASE

    /**
     * Enable detailed authentication logging.
     * Useful during migration to compare behaviors between providers.
     */
    var enableAuthDebugLogging: Boolean = true

    /**
     * Enable parallel authentication for testing.
     * When true, both Firebase and GitLive will be called simultaneously
     * and results will be compared (only Firebase result will be used).
     */
    var enableAuthParallelTesting: Boolean = false

    /**
     * Percentage of users to migrate to GitLive (0-100).
     * Used for gradual rollout based on user ID hash.
     * Only applies when authProvider is AUTO.
     */
    var gitLiveRolloutPercentage: Int = 0

    /**
     * Enable GitLive for specific user IDs (for testing).
     * Takes precedence over other settings.
     */
    val gitLiveTestUsers: Set<String> = setOf(
        // Add test user IDs here
        // "test_user_1",
        // "test_user_2"
    )

    /**
     * Use GitLive Storage instead of Firebase Storage.
     * When true, uses GitLive firebase-storage SDK (cross-platform).
     * When false, uses native Firebase Storage (Android only).
     */
    var useGitLiveStorage: Boolean = true

    /**
     * Check if GitLive should be used for a specific user.
     * This considers test users, rollout percentage, and platform.
     */
    fun shouldUseGitLive(userId: String?, platform: Platform = Platform.getCurrentPlatform()): Boolean {
        // Check explicit flag first
        if (authProvider == AuthProvider.GITLIVE) return true
        if (authProvider == AuthProvider.FIREBASE) return false

        // Check test users
        if (userId != null && gitLiveTestUsers.contains(userId)) {
            return true
        }

        // Check platform-specific logic for AUTO mode
        if (authProvider == AuthProvider.AUTO) {
            // Use GitLive for iOS (Firebase not available)
            if (platform == Platform.IOS) return true

            // Check rollout percentage for Android
            if (platform == Platform.ANDROID && userId != null) {
                val userHash = userId.hashCode().toUInt() % 100u
                return userHash.toInt() < gitLiveRolloutPercentage
            }
        }

        return false
    }
}

/**
 * Available authentication providers.
 */
enum class AuthProvider {
    /**
     * Use Firebase Authentication (Android only)
     */
    FIREBASE,

    /**
     * Use GitLive Firebase SDK (cross-platform)
     */
    GITLIVE,

    /**
     * Automatically select based on platform and rollout settings
     */
    AUTO
}

/**
 * Platform detection for feature flag decisions.
 */
enum class Platform {
    ANDROID,
    IOS,
    WEB,
    DESKTOP;

    companion object {
        /**
         * Detect the current platform.
         * Implemented using expect/actual pattern.
         */
        fun getCurrentPlatform(): Platform = getPlatform()
    }
}

/**
 * Platform detection - expect/actual pattern.
 * Each platform provides its own implementation.
 */
internal expect fun getPlatform(): Platform

/**
 * Configuration helper for easy feature flag setup.
 */
object FeatureFlagConfig {

    /**
     * Configure for production use (safe defaults).
     */
    fun configureForProduction() {
        FeatureFlags.authProvider = AuthProvider.FIREBASE
        FeatureFlags.enableAuthDebugLogging = false
        FeatureFlags.enableAuthParallelTesting = false
        FeatureFlags.gitLiveRolloutPercentage = 0
        FeatureFlags.useGitLiveStorage = false
    }

    /**
     * Configure for development/testing.
     */
    fun configureForDevelopment() {
        FeatureFlags.authProvider = AuthProvider.AUTO
        FeatureFlags.enableAuthDebugLogging = true
        FeatureFlags.enableAuthParallelTesting = true
        FeatureFlags.gitLiveRolloutPercentage = 10 // Test with 10% of users
        FeatureFlags.useGitLiveStorage = true
    }

    /**
     * Configure for GitLive testing.
     */
    fun configureForGitLiveTesting() {
        FeatureFlags.authProvider = AuthProvider.GITLIVE
        FeatureFlags.enableAuthDebugLogging = true
        FeatureFlags.enableAuthParallelTesting = false
        FeatureFlags.gitLiveRolloutPercentage = 100
        FeatureFlags.useGitLiveStorage = true
    }

    /**
     * Configure for A/B testing between providers.
     */
    fun configureForABTesting(percentage: Int = 50) {
        FeatureFlags.authProvider = AuthProvider.AUTO
        FeatureFlags.enableAuthDebugLogging = true
        FeatureFlags.enableAuthParallelTesting = true
        FeatureFlags.gitLiveRolloutPercentage = percentage.coerceIn(0, 100)
        FeatureFlags.useGitLiveStorage = true
    }
}