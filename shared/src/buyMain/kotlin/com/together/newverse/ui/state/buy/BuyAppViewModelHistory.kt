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
    val isBasketEmpty = basketRepository.observeBasket().value.isEmpty()

    if (isBasketEmpty) {
        // Basket is empty, so load the tapped order. If it's outdated, it will be treated as a new draft.
        _state.update { it.copy(navigateToBasketAsTopLevel = true) }
        val dateKey = formatDateKey(order.pickUpDate)
        handleBasketScreenAction(BuyBasketScreenAction.LoadOrder(order.id, dateKey, forceLoad = true))
    } else {
        // Basket is not empty, so always show the merge dialog.
        _state.update {
            it.copy(
                showHistoryMergeDialog = true,
                tappedHistoryOrder = order
            )
        }
    }
}

internal fun BuyAppViewModel.mergeHistoryOrder() {
    viewModelScope.launch {
        val tappedOrder = _state.value.tappedHistoryOrder ?: return@launch
        val currentBasketItems = basketRepository.observeBasket().value

        // Combine items from the current basket and the old order, summing quantities for duplicates.
        val mergedItems = (currentBasketItems + tappedOrder.articles)
            .groupBy { it.productId }
            .map { (_, items) ->
                items.first().copy(amountCount = items.sumOf { it.amountCount })
            }

        // Update the basket repository with the new merged list.
        basketRepository.clearBasket()
        mergedItems.forEach { basketRepository.addItem(it) }

        // Hide the dialog and trigger navigation to the basket, preserving the current order context.
        hideHistoryMergeDialog()
        _state.update { it.copy(navigateToBasketAsTopLevel = true) }
    }
}

internal fun BuyAppViewModel.discardAndLoadHistoryOrder() {
    val tappedOrder = _state.value.tappedHistoryOrder ?: return
    viewModelScope.launch {
        hideHistoryMergeDialog()
        // Set flag to navigate, which will be observed by the AppScaffold
        _state.update { it.copy(navigateToBasketAsTopLevel = true) }
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
