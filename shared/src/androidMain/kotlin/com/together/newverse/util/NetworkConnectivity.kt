package com.together.newverse.util

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission

private const val TAG = "NetworkConn"

/**
 * Check network connectivity status
 */
object NetworkConnectivity {
    /**
     * Check if device has active internet connection
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isConnected(context: Context): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                Log.d(TAG) { "No ConnectivityManager available" }
                return false
            }

            val network = connectivityManager.activeNetwork
            if (network == null) {
                Log.d(TAG) { "No active network" }
                return false
            }

            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities == null) {
                Log.d(TAG) { "No capabilities for active network" }
                return false
            }

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            Log.d(TAG) { "hasInternet=$hasInternet, isValidated=$isValidated" }

            return hasInternet && isValidated
        } catch (e: Exception) {
            Log.e(TAG) { "Exception: ${e.message}" }
            return false
        }
    }
}
