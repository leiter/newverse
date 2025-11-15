package com.together.newverse.data.config

/**
 * Quick configuration presets for testing the GitLive migration.
 *
 * To use: In AndroidDomainModule.kt, change the configuration line to one of these presets.
 *
 * Example:
 * ```kotlin
 * single { AuthMigrationConfig.useGitLiveOnly() }
 * ```
 */
object AuthMigrationConfig {

    /**
     * PRESET 1: Production Safe (Default)
     * Continue using Firebase for all users.
     * Use this for production builds.
     */
    fun useFirebaseOnly() {
        println("🔧 AuthMigrationConfig: Configured for FIREBASE ONLY (Production Safe)")
        FeatureFlags.authProvider = AuthProvider.FIREBASE
        FeatureFlags.enableAuthDebugLogging = false
        FeatureFlags.enableAuthParallelTesting = false
        FeatureFlags.gitLiveRolloutPercentage = 0
    }

    /**
     * PRESET 2: GitLive Testing
     * Use GitLive for all users.
     * Good for testing GitLive implementation.
     */
    fun useGitLiveOnly() {
        println("🔧 AuthMigrationConfig: Configured for GITLIVE ONLY (Testing)")
        FeatureFlags.authProvider = AuthProvider.GITLIVE
        FeatureFlags.enableAuthDebugLogging = true
        FeatureFlags.enableAuthParallelTesting = false
        FeatureFlags.gitLiveRolloutPercentage = 100
    }

    /**
     * PRESET 3: Side-by-Side Testing
     * Use Firebase as primary, but test GitLive in parallel.
     * Logs comparison results for debugging.
     */
    fun useSideBySideTesting() {
        println("🔧 AuthMigrationConfig: Configured for SIDE-BY-SIDE TESTING")
        FeatureFlags.authProvider = AuthProvider.FIREBASE
        FeatureFlags.enableAuthDebugLogging = true
        FeatureFlags.enableAuthParallelTesting = true
        FeatureFlags.gitLiveRolloutPercentage = 0
    }

    /**
     * PRESET 4: Gradual Rollout
     * Roll out GitLive to a percentage of users.
     *
     * @param percentage Percentage of users to migrate (0-100)
     */
    fun useGradualRollout(percentage: Int = 10) {
        println("🔧 AuthMigrationConfig: Configured for GRADUAL ROLLOUT ($percentage%)")
        FeatureFlags.authProvider = AuthProvider.AUTO
        FeatureFlags.enableAuthDebugLogging = true
        FeatureFlags.enableAuthParallelTesting = false
        FeatureFlags.gitLiveRolloutPercentage = percentage.coerceIn(0, 100)
    }

    /**
     * PRESET 5: Developer Mode
     * Best for development - shows all logs and comparisons.
     */
    fun useDeveloperMode() {
        println("🔧 AuthMigrationConfig: Configured for DEVELOPER MODE")
        FeatureFlags.authProvider = AuthProvider.AUTO
        FeatureFlags.enableAuthDebugLogging = true
        FeatureFlags.enableAuthParallelTesting = true
        FeatureFlags.gitLiveRolloutPercentage = 50
    }

    /**
     * Get current configuration as a readable string.
     */
    fun getCurrentConfig(): String {
        return """
            Current Auth Migration Configuration:
            - Provider: ${FeatureFlags.authProvider}
            - Debug Logging: ${FeatureFlags.enableAuthDebugLogging}
            - Parallel Testing: ${FeatureFlags.enableAuthParallelTesting}
            - GitLive Rollout: ${FeatureFlags.gitLiveRolloutPercentage}%
            - Test Users: ${FeatureFlags.gitLiveTestUsers.size} users
        """.trimIndent()
    }

    /**
     * Print current configuration to console.
     */
    fun printConfig() {
        println("=" * 50)
        println(getCurrentConfig())
        println("=" * 50)
    }
}

// Extension function for pretty printing
private operator fun String.times(n: Int): String = this.repeat(n)