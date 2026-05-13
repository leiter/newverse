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
        basketScreenLoadHistoryOrderAsReorder(order)
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
        val currentArticles = _state.value.mainScreen.articles

        // Combine items, summing quantities and correcting prices.
        val mergedItems = (currentBasketItems + tappedOrder.articles)
            .groupBy { it.productId }
            .map { (productId, items) ->
                val currentArticle = currentArticles.find { it.id == productId }
                val representativeItem = items.first()

                if (currentArticle != null && currentArticle.available) {
                    // If article exists and is available, use its current data
                    representativeItem.copy(
                        amountCount = items.sumOf { it.amountCount },
                        price = currentArticle.price,
                        productName = currentArticle.productName,
                        unit = currentArticle.unit
                    )
                } else {
                    // Otherwise, just sum the quantity but keep existing data
                    representativeItem.copy(
                        amountCount = items.sumOf { it.amountCount }
                    )
                }
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
    hideHistoryMergeDialog()
    basketScreenLoadHistoryOrderAsReorder(tappedOrder)
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
