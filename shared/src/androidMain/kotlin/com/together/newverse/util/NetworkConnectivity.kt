package com.together.newverse.util

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission

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
                println("🌐 [NetworkConnectivity] No ConnectivityManager available")
                return false
            }

            val network = connectivityManager.activeNetwork
            if (network == null) {
                println("🌐 [NetworkConnectivity] No active network")
                return false
            }

            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities == null) {
                println("🌐 [NetworkConnectivity] No capabilities for active network")
                return false
            }

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            println("🌐 [NetworkConnectivity] hasInternet=$hasInternet, isValidated=$isValidated")

            return hasInternet && isValidated
        } catch (e: Exception) {
            println("🌐 [NetworkConnectivity] Exception: ${e.message}")
            return false
        }
    }
}
