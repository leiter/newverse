package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Order
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BuyBasketScreenAction
import com.together.newverse.ui.navigation.NavRoutes
import kotlinx.coroutines.flow.update
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch

internal fun BuyAppViewModel.handleHistoryOrderTap(order: Order) {
    val isOutdated = getDaysUntilPickup(order.pickUpDate) < 0
    val isBasketEmpty = basketRepository.observeBasket().value.isEmpty()

    if (isOutdated && !isBasketEmpty) {
        _state.update {
            it.copy(
                showHistoryMergeDialog = true,
                tappedHistoryOrder = order
            )
        }
    } else {
        // Set flag to navigate after loading
        _state.update { it.copy(navigateToBasketAfterLoad = true) }

        // Load the order into the basket
        val dateKey = formatDateKey(order.pickUpDate)
        handleBasketScreenAction(BuyBasketScreenAction.LoadOrder(order.id, dateKey))
    }
}

internal fun BuyAppViewModel.mergeHistoryOrder() {
    val state = _state.value
    val tappedOrder = state.tappedHistoryOrder ?: return
    val currentBasketItems = basketRepository.observeBasket().value

    // Use the existing, robust merge conflict logic
    val conflicts = basketScreenCalculateMergeConflicts(currentBasketItems, tappedOrder.articles)

    // Hide the old dialog and show the new one by updating the basket screen state
    _state.update {
        it.copy(
            showHistoryMergeDialog = false,
            tappedHistoryOrder = null,
            basketScreen = it.basketScreen.copy(
                showMergeDialog = true,
                existingOrderForMerge = tappedOrder,
                mergeConflicts = conflicts
            ),
            // Navigate to the basket screen to show the merge dialog
            navigation = it.navigation.copy(pendingRoute = NavRoutes.Buy.Basket)
        )
    }
}

internal fun BuyAppViewModel.discardAndLoadHistoryOrder() {
    val tappedOrder = _state.value.tappedHistoryOrder ?: return
    viewModelScope.launch {
        hideHistoryMergeDialog()
        // Set flag to navigate after loading
        _state.update { it.copy(navigateToBasketAfterLoad = true) }
        // Let LoadOrder handle clearing the basket and loading the new one.
        val dateKey = formatDateKey(tappedOrder.pickUpDate)
        handleBasketScreenAction(BuyBasketScreenAction.LoadOrder(tappedOrder.id, dateKey, forceLoad = true))
    }
}

internal fun BuyAppViewModel.hideHistoryMergeDialog() {
    _state.update {
        it.copy(
            showHistoryMergeDialog = false,
            tappedHistoryOrder = null
        )
    }
}

private fun getDaysUntilPickup(pickupDate: Long): Long {
    val now = kotlin.time.Clock.System.now()
    val pickupInstant = kotlin.time.Instant.fromEpochMilliseconds(pickupDate)
    val todayDate = now.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    val pickupLocalDate = pickupInstant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    return (pickupLocalDate.toEpochDays() - todayDate.toEpochDays()).toLong()
}

private fun formatDateKey(timestamp: Long): String {
    val instant = kotlin.time.Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val day = dateTime.dayOfMonth.toString().padStart(2, '0')
    val month = dateTime.monthNumber.toString().padStart(2, '0')
    val year = dateTime.year
    return "$year$month$day"
}
