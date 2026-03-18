package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.InvitationStatus
import com.together.newverse.ui.state.BasketScreenState
import com.together.newverse.ui.state.BasketState
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BuySellerAction
import com.together.newverse.ui.state.ConnectionConfirmation
import com.together.newverse.ui.state.MainScreenState
import com.together.newverse.ui.state.OrderHistoryScreenState
import com.together.newverse.ui.state.ProductsScreenState
import com.together.newverse.ui.state.SnackbarType
import kotlin.time.Clock
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Seller connection extension functions for BuyAppViewModel.
 *
 * Handles connecting to a new seller (via QR code or manual input),
 * invitation-based connections, resetting to demo mode,
 * and reloading all seller-dependent data.
 */

internal fun BuyAppViewModel.handleSellerAction(action: BuySellerAction) {
    when (action) {
        is BuySellerAction.ConnectToSeller -> connectToSeller(action.sellerId)
        is BuySellerAction.ConnectWithToken -> connectWithToken(action.sellerId, action.buyerToken)
        is BuySellerAction.ConnectWithInvitation -> connectWithInvitation(
            action.sellerId, action.invitationId, action.expiresAt
        )
        is BuySellerAction.AcceptPendingInvitation -> acceptPendingInvitation(action.invitationId)
        is BuySellerAction.RejectPendingInvitation -> rejectPendingInvitation(action.invitationId)
        is BuySellerAction.ConfirmConnection -> confirmConnection()
        is BuySellerAction.DismissConnectionDialog -> dismissConnectionDialog()
        is BuySellerAction.ResetToDemo -> resetToDemo()
        is BuySellerAction.RequestAccess -> requestAccess()
    }
}

internal fun BuyAppViewModel.connectToSeller(sellerId: String) {
    if (sellerId.isBlank()) {
        viewModelScope.launch {
            showSnackbar("Seller ID cannot be empty", SnackbarType.ERROR)
        }
        return
    }

    // If same seller, no-op
    if (sellerId == sellerConfig.sellerId) {
        viewModelScope.launch {
            showSnackbar("Already connected to this seller", SnackbarType.INFO)
        }
        return
    }

    // Demo-to-production gating: if currently in demo mode and target is not the demo seller,
    // require invitation (unless invitationRepository is not configured)
    // TODO: Remove test seller bypass after E2E testing
    val testSellerBypass = sellerId == "jOSoP5UxAohZDtdpluhHg6VrfvQ2" || sellerId == "cPkcZSiF3LMXjWoqW6AqpA9paoO2"
    if (_state.value.isDemoMode && sellerId != sellerConfig.demoSellerId && invitationRepository != null && !testSellerBypass) {
        viewModelScope.launch {
            showSnackbar("Please scan a seller's QR code or accept an invitation", SnackbarType.ERROR)
        }
        return
    }

    performConnection(sellerId)
}

private fun BuyAppViewModel.connectWithInvitation(
    sellerId: String,
    invitationId: String,
    expiresAt: Long
) {
    val repo = invitationRepository ?: run {
        // Fallback: connect directly if no invitation repository
        performConnection(sellerId)
        return
    }

    viewModelScope.launch {
        try {
            // Client-side expiry fast-fail
            val now = Clock.System.now().toEpochMilliseconds()
            if (now >= expiresAt) {
                showSnackbar("This invitation has expired. Please request a new one.", SnackbarType.ERROR)
                return@launch
            }

            // Server-side validation
            val result = repo.getInvitation(invitationId)
            result.fold(
                onSuccess = { invitation ->
                    if (invitation.status == InvitationStatus.EXPIRED || invitation.isExpired(now)) {
                        showSnackbar("This invitation has expired. Please request a new one.", SnackbarType.ERROR)
                        return@launch
                    }
                    if (invitation.status != InvitationStatus.PENDING) {
                        showSnackbar("This invitation is no longer valid", SnackbarType.ERROR)
                        return@launch
                    }

                    // Show confirmation dialog
                    _state.update { current ->
                        current.copy(
                            showConnectionConfirmDialog = ConnectionConfirmation(
                                invitation = invitation,
                                sellerDisplayName = invitation.sellerDisplayName
                            )
                        )
                    }
                },
                onFailure = { e ->
                    showSnackbar("Invitation invalid or not found", SnackbarType.ERROR)
                }
            )
        } catch (e: Exception) {
            println("BuyAppViewModel.connectWithInvitation: Error - ${e.message}")
            showSnackbar("Failed to validate invitation: ${e.message}", SnackbarType.ERROR)
        }
    }
}

private fun BuyAppViewModel.confirmConnection() {
    val confirmation = _state.value.showConnectionConfirmDialog ?: return
    val invitation = confirmation.invitation
    val repo = invitationRepository ?: return

    viewModelScope.launch {
        try {
            val buyerId = authRepository.getCurrentUserId()
            if (buyerId == null) {
                showSnackbar("Not authenticated", SnackbarType.ERROR)
                return@launch
            }

            // Accept invitation on server
            repo.acceptInvitation(invitation.id, buyerId).fold(
                onSuccess = {
                    // Dismiss dialog
                    _state.update { it.copy(showConnectionConfirmDialog = null) }
                    // Perform the actual connection
                    performConnection(invitation.sellerId)
                },
                onFailure = { e ->
                    _state.update { it.copy(showConnectionConfirmDialog = null) }
                    showSnackbar("Failed to accept invitation: ${e.message}", SnackbarType.ERROR)
                }
            )
        } catch (e: Exception) {
            _state.update { it.copy(showConnectionConfirmDialog = null) }
            showSnackbar("Failed to connect: ${e.message}", SnackbarType.ERROR)
        }
    }
}

private fun BuyAppViewModel.dismissConnectionDialog() {
    _state.update { it.copy(showConnectionConfirmDialog = null) }
}

private fun BuyAppViewModel.acceptPendingInvitation(invitationId: String) {
    val repo = invitationRepository ?: return

    viewModelScope.launch {
        val buyerId = authRepository.getCurrentUserId() ?: return@launch

        repo.acceptInvitation(invitationId, buyerId).fold(
            onSuccess = { invitation ->
                // Remove from pending list
                _state.update { current ->
                    current.copy(
                        pendingInvitations = current.pendingInvitations.filter { it.id != invitationId }
                    )
                }
                // Connect to seller
                performConnection(invitation.sellerId)
            },
            onFailure = { e ->
                showSnackbar("Failed to accept invitation: ${e.message}", SnackbarType.ERROR)
            }
        )
    }
}

private fun BuyAppViewModel.rejectPendingInvitation(invitationId: String) {
    val repo = invitationRepository ?: return

    viewModelScope.launch {
        val buyerId = authRepository.getCurrentUserId() ?: return@launch

        repo.rejectInvitation(invitationId, buyerId).fold(
            onSuccess = {
                _state.update { current ->
                    current.copy(
                        pendingInvitations = current.pendingInvitations.filter { it.id != invitationId }
                    )
                }
            },
            onFailure = { e ->
                showSnackbar("Failed to reject invitation: ${e.message}", SnackbarType.ERROR)
            }
        )
    }
}

internal fun BuyAppViewModel.performConnection(sellerId: String) {
    viewModelScope.launch {
        try {
            // Check if buyer is blocked by this seller
            val buyerId = authRepository.getCurrentUserId()
            if (buyerId != null) {
                val isBlocked = profileRepository.isClientBlocked(sellerId, buyerId)
                if (isBlocked) {
                    showSnackbar("You have been blocked by this seller", SnackbarType.ERROR)
                    return@launch
                }

                // Register as known client (non-fatal — may fail for demo/unapproved buyers)
                try {
                    profileRepository.addKnownClient(sellerId, buyerId)
                } catch (e: Exception) {
                    println("BuyAppViewModel.performConnection: addKnownClient failed (non-fatal) - ${e.message}")
                }
            }

            // Persist new seller ID
            sellerConfig.setSellerId(sellerId)

            // Clear the actual basket repository (drives the cart badge)
            basketRepository.clearBasket()
            // Clear draft basket from Firebase (non-fatal — may fail for demo/unapproved buyers)
            try {
                profileRepository.clearDraftBasket()
            } catch (e: Exception) {
                println("BuyAppViewModel.performConnection: clearDraftBasket failed (non-fatal) - ${e.message}")
            }

            // Fetch seller display name
            val sellerDisplayName = try {
                profileRepository.getSellerProfile(sellerId).getOrNull()?.displayName ?: ""
            } catch (e: Exception) {
                println("BuyAppViewModel.performConnection: getSellerProfile failed (non-fatal) - ${e.message}")
                ""
            }

            // Clear basket and reset screen states; reset accessStatus (will be re-observed)
            _state.update { current ->
                current.copy(
                    connectedSellerId = sellerConfig.sellerId,
                    connectedSellerDisplayName = sellerDisplayName,
                    accessStatus = AccessStatus.NONE,
                    basket = BasketState(),
                    products = ProductsScreenState(),
                    mainScreen = MainScreenState(),
                    basketScreen = BasketScreenState(),
                    orderHistory = OrderHistoryScreenState()
                )
            }

            // Reload all seller-dependent data
            loadProducts()
            loadMainScreenArticles()

            // Re-observe access status for the new seller
            startObservingAccessStatus()

            showSnackbar("Connected to seller", SnackbarType.SUCCESS)

        } catch (e: Exception) {
            println("BuyAppViewModel.performConnection: Error - ${e.message}")
            showSnackbar("Failed to connect: ${e.message}", SnackbarType.ERROR)
        }
    }
}

private fun BuyAppViewModel.resetToDemo() {
    // Reset to demo bypasses invitation check
    performConnection(sellerConfig.demoSellerId)
}
