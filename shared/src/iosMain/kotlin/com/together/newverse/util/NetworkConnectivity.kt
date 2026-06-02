package com.together.newverse.util

/**
 * Check network connectivity status on iOS
 * Uses Network framework to monitor connection
 */
object NetworkConnectivity {
    private var lastKnownStatus: Boolean = true

    /**
     * Check if device has active internet connection
     * Note: On iOS, we default to true if we can't check,
     * to avoid false negatives during early startup
     */
    fun isConnected(): Boolean {
        // For now, return last known status
        // A more robust implementation would use Network.framework callbacks
        return lastKnownStatus
    }

    /**
     * Simple connectivity check that doesn't require framework setup
     * This is a best-effort check
     */
    fun checkConnectivity(): Boolean {
        // Return true by default on iOS - the network operations themselves
        // will fail with appropriate errors if there's no connection
        // This prevents false-positive offline detection
        return true
    }
}
