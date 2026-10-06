package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.SnackbarType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.together.newverse.util.Log


private const val TAG = "BuyVMAccess"

/**
 * Tracks which required profile fields are missing.
 */
internal data class MissingProfileData(
    val name: Boolean = false,
    val pickupTime: Boolean = false,
    val street: Boolean = false,     // only relevant if !isSelfPickup
    val houseNumber: Boolean = false  // only relevant if !isSelfPickup
) {
    val isComplete: Boolean get() = !name && !pickupTime && !street && !houseNumber
    val allMissing: Boolean get() = name && pickupTime && (street || houseNumber)
}

/**
 * Check whether the buyer profile has all required fields filled.
 */
internal fun BuyAppViewModel.checkProfileCompleteness(): MissingProfileData {
    val profile = _state.value.customerProfile.profile
    val isSelfPickup = profile?.isSelfPickup ?: false
    return MissingProfileData(
        name = profile?.displayName.isNullOrBlank(),
        pickupTime = profile?.defaultPickUpTime.isNullOrBlank(),
        street = if (isSelfPickup) false else profile?.street.isNullOrBlank(),
        houseNumber = if (isSelfPickup) false else profile?.houseNumber.isNullOrBlank()
    )
}

/**
 * Build a user-facing message listing which fields are still missing.
 */
internal fun buildMissingFieldsMessage(missing: MissingProfileData): String {
    val fields = mutableListOf<String>()
    if (missing.name) fields.add("Name")
    if (missing.pickupTime) fields.add("Abholzeit")
    if (missing.street) fields.add("Straße")
    if (missing.houseNumber) fields.add("Hausnummer")
    return "Fehlende Angaben: ${fields.joinToString(", ")}"
}

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
    val sellerId = sellerConfig.sellerId
    if (sellerId.isEmpty()) return

    // No token gate: the access record is keyed by this buyer's own uid, so the
    // observer always has something to watch. An absent record reports NONE, which
    // is what puts the buyer in demo mode.
    viewModelScope.launch {
        profileRepository.observeAccessStatus(sellerId)
            .catch { e ->
                Log.e(TAG) { "observeAccessStatus error: ${e.message}" }
                _state.update { it.copy(isAccessStatusLoaded = true) }
            }
            .collect { status ->
                Log.d(TAG) { "observeAccessStatus: status=$status" }
                val wasDemoMode = _state.value.isDemoMode
                _state.update { it.copy(accessStatus = status, isAccessStatusLoaded = true) }
                when {
                    wasDemoMode && status == AccessStatus.APPROVED -> {
                        // Demo → Production: migrate local demo orders to Firebase orders/
                        migrateLocalDemoOrdersToProduction()
                    }
                    !wasDemoMode && status != AccessStatus.APPROVED -> {
                        // Production → Demo: reset counter so the 2-Firebase-order rule applies again
                        sellerConfig.resetDemoOrderState()
                    }
                }
            }
    }
}

/**
 * Request access from the connected seller.
 * The request is keyed by the buyer's auth uid, so there is no identifier to mint.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun BuyAppViewModel.requestAccess() {
    val sellerId = sellerConfig.sellerId
    if (sellerId.isEmpty()) {
        viewModelScope.launch {
            showSnackBar("Bitte zuerst mit einem Verkäufer verbinden", SnackbarType.ERROR)
        }
        return
    }

    // Check profile completeness before requesting access
    val missing = checkProfileCompleteness()
    if (!missing.isComplete) {
        if (missing.allMissing) {
            _state.update { it.copy(showProfileIncompleteDialog = true) }
        } else {
            viewModelScope.launch {
                showSnackBar(buildMissingFieldsMessage(missing), SnackbarType.WARNING)
            }
        }
        _state.update { it.copy(isRequestingAccess = false) }
        return
    }

    _state.update { it.copy(isRequestingAccess = true) }

    viewModelScope.launch {
        try {
            val displayName = _state.value.customerProfile.profile?.displayName
                ?.takeIf { it.isNotBlank() } ?: "Guest"

            profileRepository.submitAccessRequest(sellerId, displayName)
                .onSuccess {
                    _state.update { it.copy(isRequestingAccess = false) }
                    startObservingAccessStatus()
                    showSnackBar("Zugangsanfrage gesendet", SnackbarType.SUCCESS)
                }
                .onFailure { e ->
                    _state.update { it.copy(isRequestingAccess = false) }
                    showSnackBar("Zugangsanfrage fehlgeschlagen: ${e.message}", SnackbarType.ERROR)
                }
        } catch (e: Exception) {
            _state.update { it.copy(isRequestingAccess = false) }
            showSnackBar("Zugangsanfrage fehlgeschlagen: ${e.message}", SnackbarType.ERROR)
        }
    }
}

/**
 * Apply a pre-approved UUID (from invitation or QR link).
 * Sets the UUID locally and in Firebase, then starts observing access status.
 * The seller already wrote APPROVED for this UUID, so the buyer sees it immediately.
 */
/**
 * Dismiss the profile incomplete dialog.
 */
internal fun BuyAppViewModel.dismissProfileIncompleteDialog() {
    _state.update { it.copy(showProfileIncompleteDialog = false) }
}

/**
 * Retry connecting with a previously stored pending token after profile is completed.
 */
/**
 * Migrate all locally stored demo orders to the production Firebase orders/ path.
 * Called when a buyer transitions from demo mode to APPROVED status.
 * Clears local demo storage and resets the demo counter after successful upload.
 */
internal fun BuyAppViewModel.migrateLocalDemoOrdersToProduction() {
    viewModelScope.launch {
        val localOrders = sellerConfig.loadDemoOrders()
        if (localOrders.isEmpty()) {
            sellerConfig.clearDemoOrders()
            sellerConfig.resetDemoOrderState()
            return@launch
        }
        Log.d(TAG) { "migrateLocalDemoOrdersToProduction: Migrating ${localOrders.size} orders to production Firebase" }
        var migratedCount = 0
        for (demoOrder in localOrders) {
            orderRepository.placeOrder(demoOrder.copy(isDemoOrder = false))
                .onSuccess {
                    migratedCount++
                    Log.d(TAG) { "migrateLocalDemoOrdersToProduction: Migrated order ${demoOrder.id}" }
                }
                .onFailure { e ->
                    Log.d(TAG) { "migrateLocalDemoOrdersToProduction: Failed for ${demoOrder.id}: ${e.message}" }
                }
        }
        sellerConfig.clearDemoOrders()
        sellerConfig.resetDemoOrderState()
        loadOrderHistory()
        Log.d(TAG) { "migrateLocalDemoOrdersToProduction: Done — $migratedCount/${localOrders.size} migrated" }
    }
}

internal fun BuyAppViewModel.retryPendingConnection() {
    val pending = _state.value.pendingConnectToken
    _state.update { it.copy(
        showProfileIncompleteDialog = false,
        pendingConnectToken = null
    ) }
    if (pending != null) {
        connectWithToken(pending.first, pending.second)
    }
}

/**
 * Redeem an invitation, which doubles as its own invite token.
 * Same path as a scanned QR link - see [connectWithToken].
 */
internal fun BuyAppViewModel.applyPreApprovedAccess(token: String) {
    connectWithToken(sellerConfig.sellerId, token)
}

/**
 * Handle a connect deep link: newverse://connect?seller={sellerId}&token={token}
 *
 * The token is a bearer ticket the seller minted before this buyer existed. Redeeming
 * it marks it used and writes an APPROVED access record citing it, which the rules
 * verify against the token's redeemedBy. If the token is already spent, expired or
 * unknown, redemption fails and the buyer falls back to a normal access request.
 */
internal fun BuyAppViewModel.connectWithToken(sellerId: String, token: String) {
    if (sellerId.isEmpty() || token.isEmpty()) return

    val missing = checkProfileCompleteness()
    if (!missing.isComplete) {
        // Hold the token so redemption can be retried once the profile is complete.
        _state.update {
            it.copy(
                pendingConnectToken = Pair(sellerId, token),
                showProfileIncompleteDialog = true
            )
        }
        return
    }

    pendingTokenStorage?.set(token)

    // Bypass the demo mode gate: holding a token means the seller authorised this buyer.
    performConnection(sellerId)

    viewModelScope.launch {
        val displayName = _state.value.customerProfile.profile?.displayName
            ?.takeIf { it.isNotBlank() } ?: "Guest"

        when (profileRepository.getAccessStatus(sellerId)) {
            AccessStatus.APPROVED, AccessStatus.BLOCKED -> {
                // Already settled with this seller; never overwrite it with a redemption.
                Log.d(TAG) { "connectWithToken: access already settled, skipping redemption" }
                pendingTokenStorage?.clear()
            }
            else -> {
                profileRepository.redeemInviteToken(sellerId, token, displayName)
                    .onSuccess {
                        // The ticket is spent; identity is the auth uid from here on.
                        pendingTokenStorage?.clear()
                        Log.d(TAG) { "connectWithToken: token redeemed, access approved" }
                    }
                    .onFailure { e ->
                        Log.w(TAG) { "connectWithToken: redemption failed - ${e.message}" }
                        profileRepository.submitAccessRequest(sellerId, displayName)
                            .onSuccess { Log.d(TAG) { "connectWithToken: fell back to an access request" } }
                            .onFailure { err -> Log.e(TAG) { "connectWithToken: request failed - ${err.message}" } }
                    }
            }
        }

        startObservingAccessStatus()
    }
}
