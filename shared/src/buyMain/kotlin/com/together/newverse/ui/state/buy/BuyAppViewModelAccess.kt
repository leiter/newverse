package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.ui.state.BuyAppViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Access request extension functions for BuyAppViewModel.
 *
 * Handles:
 * - startObservingAccessStatus — starts real-time Firebase listener for buyer access status
 * - connectWithToken — handles deep link with seller-assigned UUID token
 */

/**
 * Start observing access status from Firebase in real-time.
 * Updates accessStatus (and therefore isDemoMode) automatically.
 * Called after auth+seller connection is confirmed.
 */
internal fun BuyAppViewModel.startObservingAccessStatus() {
    val uuid = buyerUUIDStorage?.get() ?: return
    val sellerId = sellerConfig.sellerId
    if (sellerId.isEmpty()) return

    viewModelScope.launch {
        profileRepository.observeAccessStatus(uuid, sellerId)
            .catch { e -> println("[NV_Access] observeAccessStatus error (permission?): ${e.message}") }
            .collect { status ->
                println("[NV_Access] observeAccessStatus: status=$status uuid=$uuid")
                _state.update { it.copy(accessStatus = status) }
            }
    }
}

/**
 * Handle deep link: newverse://connect?seller={sellerId}&token={buyerToken}
 *
 * 1. Persist the buyerUUID from the token
 * 2. Connect to seller (reload products, reset screens)
 * 3. Submit an access request if current status is NONE/absent
 * 4. Start observing access status for real-time updates
 */
internal fun BuyAppViewModel.connectWithToken(sellerId: String, buyerToken: String) {
    buyerUUIDStorage?.set(buyerToken)

    // Reuse existing connect-to-seller flow (stores sellerId, reloads products, resets state)
    connectToSeller(sellerId)

    viewModelScope.launch {
        // Ensure buyerUUID is persisted in Firebase profile so security rules allow reading status
        profileRepository.saveBuyerUUID(buyerToken)

        // Fetch current status first — never overwrite APPROVED or BLOCKED
        val currentStatus = profileRepository.getAccessStatus(buyerToken, sellerId)
        println("[NV_Access] connectWithToken: currentStatus=$currentStatus uuid=$buyerToken")

        if (currentStatus == AccessStatus.NONE) {
            val displayName = _state.value.customerProfile.profile?.displayName?.takeIf { it.isNotBlank() } ?: "Guest"
            profileRepository.submitAccessRequest(sellerId, buyerToken, displayName)
                .onSuccess { println("[NV_Access] connectWithToken: Access request submitted") }
                .onFailure { e -> println("[NV_Access] connectWithToken: Failed to submit request - ${e.message}") }
        }

        startObservingAccessStatus()
    }
}
