package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BuySellerAction
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Invitation observation extension functions for BuyAppViewModel.
 *
 * Observes pending seller-initiated invitations and updates state.
 */

internal fun BuyAppViewModel.handleInvitationAction(action: BuySellerAction) {
    // Delegation to handleSellerAction - invitations are part of seller actions
    handleSellerAction(action)
}

internal fun BuyAppViewModel.observePendingInvitations() {
    val repo = invitationRepository ?: return

    viewModelScope.launch {
        val buyerId = authRepository.getCurrentUserId() ?: return@launch

        repo.observePendingInvitations(buyerId)
            .catch { e ->
                println("BuyAppViewModel.observePendingInvitations: Error - ${e.message}")
            }
            .collect { invitations ->
                _state.update { current ->
                    current.copy(pendingInvitations = invitations)
                }
            }
    }
}
