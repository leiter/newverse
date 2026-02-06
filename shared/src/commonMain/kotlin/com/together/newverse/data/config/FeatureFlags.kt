package com.together.newverse.data.config

/**
 * Feature flags for controlling application behavior.
 */
object FeatureFlags {

    /**
     * Enable detailed authentication logging.
     */
    var enableAuthDebugLogging: Boolean = true

    /**
     * Use GitLive Storage instead of native Firebase Storage.
     * When true, uses GitLive firebase-storage SDK (cross-platform).
     * When false, uses native Firebase Storage (Android only).
     */
    var useGitLiveStorage: Boolean = true
}

/**
 * Platform detection for platform-specific decisions.
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
     * Configure for production use.
     */
    fun configureForProduction() {
        FeatureFlags.enableAuthDebugLogging = false
        FeatureFlags.useGitLiveStorage = true
    }

    /**
     * Configure for development/testing.
     */
    fun configureForDevelopment() {
        FeatureFlags.enableAuthDebugLogging = true
        FeatureFlags.useGitLiveStorage = true
    }
}
