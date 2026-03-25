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
    val uuid = buyerUUIDStorage?.get()
    val sellerId = sellerConfig.sellerId
    if (sellerId.isEmpty()) return

    if (uuid == null) {
        // No personal invite — buyer is in demo mode, show banner immediately
        _state.update { it.copy(isAccessStatusLoaded = true) }
        return
    }

    viewModelScope.launch {
        profileRepository.observeAccessStatus(uuid, sellerId)
            .catch { e -> println("[NV_Access] observeAccessStatus error (permission?): ${e.message}") }
            .collect { status ->
                println("[NV_Access] observeAccessStatus: status=$status uuid=$uuid")
                val wasDemoMode = _state.value.isDemoMode
                _state.update { it.copy(accessStatus = status, isAccessStatusLoaded = true) }
                // Clean up demo orders when access is approved
                if (wasDemoMode && status == AccessStatus.APPROVED) {
                    sellerConfig.clearDemoOrders()
                }
            }
    }
}

/**
 * Request access from the connected seller.
 * Generates a buyerUUID if not already assigned, persists it, and submits an access request.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun BuyAppViewModel.requestAccess() {
    val sellerId = sellerConfig.sellerId
    if (sellerId.isEmpty()) {
        viewModelScope.launch {
            showSnackbar("Bitte zuerst mit einem Verkäufer verbinden", SnackbarType.ERROR)
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
                showSnackbar(buildMissingFieldsMessage(missing), SnackbarType.WARNING)
            }
        }
        _state.update { it.copy(isRequestingAccess = false) }
        return
    }

    _state.update { it.copy(isRequestingAccess = true) }

    viewModelScope.launch {
        try {
            // Reuse existing UUID or generate a new one
            val uuid = buyerUUIDStorage?.get() ?: Uuid.random().toString()

            // Persist UUID locally and in Firebase profile
            buyerUUIDStorage?.set(uuid)
            profileRepository.saveBuyerUUID(uuid)

            // Get display name from profile or default
            val displayName = _state.value.customerProfile.profile?.displayName
                ?.takeIf { it.isNotBlank() } ?: "Guest"

            // Submit the access request
            profileRepository.submitAccessRequest(sellerId, uuid, displayName)
                .onSuccess {
                    _state.update { it.copy(isRequestingAccess = false) }
                    startObservingAccessStatus()
                    showSnackbar("Zugangsanfrage gesendet", SnackbarType.SUCCESS)
                }
                .onFailure { e ->
                    _state.update { it.copy(isRequestingAccess = false) }
                    showSnackbar("Zugangsanfrage fehlgeschlagen: ${e.message}", SnackbarType.ERROR)
                }
        } catch (e: Exception) {
            _state.update { it.copy(isRequestingAccess = false) }
            showSnackbar("Zugangsanfrage fehlgeschlagen: ${e.message}", SnackbarType.ERROR)
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

internal fun BuyAppViewModel.applyPreApprovedAccess(uuid: String) {
    buyerUUIDStorage?.set(uuid)
    viewModelScope.launch {
        profileRepository.saveBuyerUUID(uuid)
        startObservingAccessStatus()
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
    // Check profile completeness before connecting
    val missing = checkProfileCompleteness()
    if (!missing.isComplete) {
        // Store the pending token for later retry
        _state.update { it.copy(
            pendingConnectToken = Pair(sellerId, buyerToken),
            showProfileIncompleteDialog = true
        ) }
        return
    }

    val oldUUID = buyerUUIDStorage?.get()
    buyerUUIDStorage?.set(buyerToken)

    // Bypass the demo mode gate — having a valid token means the seller has authorized this buyer.
    // connectToSeller would block a demo-mode buyer from connecting to a real seller.
    performConnection(sellerId)

    viewModelScope.launch {
        // Cancel any previously submitted request under a different UUID
        if (oldUUID != null && oldUUID != buyerToken) {
            profileRepository.cancelAccessRequest(sellerId, oldUUID)
                .onFailure { e -> println("[NV_Access] connectWithToken: Failed to cancel old request ($oldUUID) - ${e.message}") }
        }

        // Ensure buyerUUID is persisted in Firebase profile so security rules allow reading status
        profileRepository.saveBuyerUUID(buyerToken)

        // Fetch current status first — never overwrite APPROVED or BLOCKED
        val currentStatus = profileRepository.getAccessStatus(buyerToken, sellerId)
        println("[NV_Access] connectWithToken: currentStatus=$currentStatus uuid=$buyerToken")

        val displayName = _state.value.customerProfile.profile?.displayName?.takeIf { it.isNotBlank() } ?: "Guest"

        if (currentStatus == AccessStatus.NONE) {
            profileRepository.submitAccessRequest(sellerId, buyerToken, displayName)
                .onSuccess { println("[NV_Access] connectWithToken: Access request submitted") }
                .onFailure { e -> println("[NV_Access] connectWithToken: Failed to submit request - ${e.message}") }
        } else if (currentStatus == AccessStatus.APPROVED) {
            // QR pre-approval case: update display name from placeholder to real name
            profileRepository.updateApprovedBuyerDisplayName(sellerId, buyerToken, displayName)
                .onSuccess { println("[NV_Access] connectWithToken: Updated buyer display name") }
                .onFailure { e -> println("[NV_Access] connectWithToken: Failed to update display name - ${e.message}") }
        }

        startObservingAccessStatus()
    }
}
