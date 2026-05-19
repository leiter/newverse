package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.state.BasketMergeMode
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.MergeConflictType
import com.together.newverse.ui.state.MergeResolution
import com.together.newverse.ui.navigation.NavRoutes
import kotlinx.coroutines.flow.update
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch

internal fun BuyAppViewModel.handleHistoryOrderTap(order: Order) {
    val daysUntilPickup = getDaysUntilPickup(order.pickUpDate)
    val isOutstandingOrder = daysUntilPickup >= 0

    // For outstanding orders (pickup not defined or in the future), load directly into basket
    // without merge/edit options. Clear existing basket and load as fresh draft.
    if (isOutstandingOrder) {
        viewModelScope.launch {
            basketRepository.clearBasket()
            val currentArticles = _state.value.mainScreen.articles
            val correctedItems = order.articles.map { correctArticleData(it, currentArticles) }
            for (item in correctedItems) {
                basketRepository.addItem(item)
            }
            _state.update {
                it.copy(
                    navigateToBasketAsTopLevel = true,
                    showHistoryMergeDialog = false,
                    tappedHistoryOrder = null
                )
            }
        }
        return
    }

    // For past orders, use original logic (reorder or merge)
    val isBasketEmpty = basketRepository.observeBasket().value.isEmpty()

    if (isBasketEmpty) {
        basketScreenLoadHistoryOrderAsReorder(order)
    } else {
        // Basket is not empty, so show the merge dialog.
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

        val historicItems = tappedOrder.articles.map { correctArticleData(it, currentArticles) }
        val draftItems = currentBasketItems.map { correctArticleData(it, currentArticles) }

        val conflicts = basketScreenCalculateMergeConflicts(
            newItems = draftItems,
            existingItems = historicItems
        )
        val hasQuantityConflict = conflicts.any { it.conflictType == MergeConflictType.QUANTITY_CHANGED }

        if (!hasQuantityConflict) {
            val mergedItems = mergeItemsWithResolutions(
                historicItems = historicItems,
                draftItems = draftItems,
                conflicts = conflicts
            )
            basketRepository.clearBasket()
            for (item in mergedItems) basketRepository.addItem(item)

            // If there is already a placed order loaded for this pickup date, keep its
            // metadata so the basket screen continues to offer "Update Order".
            val basketState = _state.value.basketScreen
            val hasPlacedOrder = basketState.orderId != null
            val hasChanges = if (hasPlacedOrder)
                basketScreenCheckIfHasChanges(mergedItems, basketState.originalOrderItems) else false

            _state.update { current ->
                current.copy(
                    navigateToBasketAsTopLevel = true,
                    showHistoryMergeDialog = false,
                    tappedHistoryOrder = null,
                    orderHistory = current.orderHistory, // Keep existing state
                    basketScreen = current.basketScreen.copy(
                        items = mergedItems,
                        total = mergedItems.sumOf { it.price * it.amountCount },
                        hasChanges = hasChanges
                    )
                )
            }
        } else {
            _state.update { current ->
                current.copy(
                    navigateToBasketAsTopLevel = true,
                    showHistoryMergeDialog = false,
                    tappedHistoryOrder = null,
                    basketScreen = current.basketScreen.copy(
                        items = draftItems,
                        showMergeDialog = true,
                        mergeConflicts = conflicts,
                        existingOrderForMerge = tappedOrder.copy(articles = historicItems),
                        isMerging = false,
                        mergeMode = BasketMergeMode.HISTORY_REORDER
                    )
                )
            }
        }
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

internal fun correctArticleData(item: OrderedProduct, currentArticles: List<Article>): OrderedProduct {
    val article = currentArticles.find { it.id == item.productId }
    return if (article != null && article.available) {
        item.copy(
            price = article.price,
            productName = article.productName,
            unit = article.unit
        )
    } else item
}

internal fun mergeItemsWithResolutions(
    historicItems: List<OrderedProduct>,
    draftItems: List<OrderedProduct>,
    conflicts: List<com.together.newverse.ui.state.MergeConflict>
): List<OrderedProduct> {
    val merged = mutableListOf<OrderedProduct>()
    val processedIds = mutableSetOf<String>()

    for (historic in historicItems) {
        val draft = draftItems.find { it.productId == historic.productId }
        val conflict = conflicts.find { it.productId == historic.productId }

        val finalItem = when {
            conflict != null -> when (conflict.resolution) {
                MergeResolution.ADD -> historic.copy(
                    amountCount = historic.amountCount + (draft?.amountCount ?: 0.0),
                    price = draft?.price ?: historic.price
                )
                MergeResolution.KEEP_EXISTING -> historic
                MergeResolution.USE_NEW -> draft ?: historic
                MergeResolution.UNDECIDED -> historic
            }
            draft != null -> draft // same productId, same qty — single copy
            else -> historic
        }
        merged.add(finalItem)
        processedIds.add(historic.productId)
    }

    for (draft in draftItems) {
        if (draft.productId !in processedIds) merged.add(draft)
    }
    return merged
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
