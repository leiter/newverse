package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderOrigin
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.prepareForReorder
import com.together.newverse.domain.model.UnorderableItem
import com.together.newverse.domain.model.articleKey
import com.together.newverse.domain.model.orderableOnly
import com.together.newverse.domain.model.unorderableAgainst
import com.together.newverse.ui.state.BasketMergeMode
import com.together.newverse.ui.state.BasketScreenState
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.MergeConflict
import com.together.newverse.ui.state.MergeConflictType
import com.together.newverse.ui.state.MergeResolution
import com.together.newverse.ui.state.BuyBasketScreenAction
import com.together.newverse.ui.state.BuyUiAction
import com.together.newverse.domain.model.DraftBasket
import com.together.newverse.domain.model.MarketPricing
import com.together.newverse.domain.model.DeliveryAddress
import com.together.newverse.domain.model.Fulfilment
import com.together.newverse.domain.model.PriceChange
import com.together.newverse.domain.model.priceChanges
import com.together.newverse.ui.state.PendingSlotPriceChange
import com.together.newverse.ui.state.SnackbarType
import com.together.newverse.ui.state.BuyAppState
import com.together.newverse.domain.model.PickupSlot
import com.together.newverse.domain.model.PickupSlots
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import com.together.newverse.util.Log


private const val TAG = "BuyVMBasket"

/**
 * The provenance an order placed right now carries: [OrderOrigin.PRODUCTION] once the
 * buyer is known to be approved, [OrderOrigin.DEMO] in every other case.
 * [OrderOrigin.TRANSITIONED] is never set here — only adopting a demo order mints it.
 *
 * Keyed on [BuyAppState.isApproved] and deliberately not on `!isDemoMode`: the latter is
 * false while the access status is still loading, so a buyer who reached checkout before
 * the first emission placed a real order in `orders/` — binding, visible to the seller,
 * and past every demo guard. Origin also picks the trunk the order is written to
 * (`rootRef(order.origin.isDemo)`), so guessing wrong here puts it beyond reach of the
 * containment as well.
 */
internal fun BuyAppViewModel.demoOrProduction(): OrderOrigin =
    if (_state.value.isApproved) OrderOrigin.PRODUCTION else OrderOrigin.DEMO

/**
 * Basket Screen extension functions for BuyAppViewModel
 *
 * Handles cart/basket operations, checkout flow, order management, and date handling.
 * This is the largest domain in the ViewModel with ~30 functions.
 *
 * Extracted functions:
 * - Initialization: initializeBasketScreen, observeBasketScreenItems
 * - Action handler: handleBasketScreenAction
 * - Item management: basketScreenAddItem, basketScreenRemoveItem, basketScreenUpdateQuantity, basketScreenClearBasket
 * - Checkout: basketScreenCheckout
 * - Order loading: basketScreenLoadMostRecentEditableOrder, basketScreenLoadOrder
 * - Order editing: basketScreenEnableEditing, basketScreenUpdateOrder, basketScreenCancelOrder, basketScreenResetOrderState
 * - Date handling: basketScreenLoadAvailableDates, basketScreenShowDatePicker, basketScreenHideDatePicker, basketScreenSelectPickupDate
 * - Reorder: basketScreenShowReorderDatePicker, basketScreenHideReorderDatePicker, basketScreenReorderWithNewDate
 * - Merge: basketScreenCalculateMergeConflicts, basketScreenHideMergeDialog, basketScreenResolveMergeConflict, basketScreenConfirmMerge
 * - Helpers: basketScreenCheckIfHasChanges, basketScreenFormatDateKey
 */

// Debounce delay for saving draft basket (ms)
private const val DRAFT_SAVE_DEBOUNCE_MS = 2000L

// Flip to true to surface verbose basket-flow tracing in Logcat.
private const val LOG_BASKET = false

private fun bLog(message: String) {
    if (LOG_BASKET) Log.d(TAG) { message }
}

/**
 * Reset all in-flight basket flags and set/clear the basket-screen error message.
 * Used as the common exit point for checkout / update / cancel / merge / reorder failures.
 */
/**
 * The basket items the seller can no longer accept, judged against the catalogue as
 * the app last read it.
 *
 * Checked at the point of ordering rather than only when the catalogue is drawn: a
 * basket is persisted as a draft and reordered weeks later, and the seller can
 * withdraw an article in between. See
 * `doc/guides/stock-driven-availability-design.md` § "The gate at placement".
 */
internal fun BuyAppViewModel.unorderableInBasket(items: List<OrderedProduct>): List<UnorderableItem> =
    items.unorderableAgainst(_state.value.mainScreen.articles)

/**
 * What to tell the buyer about items the seller cannot accept.
 *
 * Names them: "etwas in Ihrem Warenkorb" would send the buyer to compare a basket
 * against a catalogue by hand.
 */
internal fun unorderableBasketMessage(unorderable: List<UnorderableItem>): String {
    val names = unorderable.map { it.productName.ifEmpty { "Artikel" } }.distinct()
    val list = names.joinToString(", ")
    return if (names.size == 1) {
        "$list ist nicht mehr verfügbar. Bitte aus dem Warenkorb entfernen."
    } else {
        "Diese Artikel sind nicht mehr verfügbar: $list. Bitte aus dem Warenkorb entfernen."
    }
}

/** What to tell the buyer about items a reorder left behind. */
internal fun droppedFromReorderMessage(dropped: List<UnorderableItem>): String {
    val names = dropped.map { it.productName.ifEmpty { "Artikel" } }.distinct().joinToString(", ")
    return "Nicht mehr verfügbar und daher nicht übernommen: $names"
}

/**
 * This order's edit deadline, worded for the buyer.
 *
 * Computed from its own pickup day, never stated as a fixed weekday. The deadline is a
 * fixed *distance* before pickup — Tuesday for a Thursday market, Thursday for a Saturday
 * one — which is what `Market.scheduleConfig` encodes and what these messages used to
 * contradict by naming Tuesday outright.
 */
private fun editDeadlineText(pickupDate: Long): String =
    OrderDateUtils.formatDisplayDateTime(
        OrderDateUtils.calculateEditDeadline(Instant.fromEpochMilliseconds(pickupDate))
    )

private fun BuyAppViewModel.setBasketError(message: String?) {
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                isCheckingOut = false,
                isCancelling = false,
                isMerging = false,
                isReordering = false,
                // An outcome is one thing or the other. Without this the previous
                // success banner stayed on screen underneath the new error, which is
                // how "erfolgreich aktualisiert" and "kann nicht mehr bearbeitet
                // werden" ended up contradicting each other on the same screen.
                orderSuccess = false,
                reorderSuccess = false,
                cancelSuccess = false,
                orderError = message
            )
        )
    }
}

/**
 * Drop a placedOrderIds entry that points at the given orderId. Best-effort: swallows
 * exceptions and logs them. No-op if the order isn't currently referenced.
 */
private suspend fun BuyAppViewModel.removePlacedOrderIdReference(orderId: String) {
    try {
        val profile = profileRepository.getBuyerProfile().getOrNull() ?: return
        val pruned = profile.placedOrderIds.filterValues { it != orderId }
        if (pruned.size == profile.placedOrderIds.size) return
        profileRepository.saveBuyerProfile(profile.copy(placedOrderIds = pruned))
        bLog("🛒 removePlacedOrderIdReference: Removed $orderId from buyer profile")
    } catch (e: Exception) {
        bLog("🛒 removePlacedOrderIdReference: Failed - ${e.message}")
    }
}

/**
 * Load the buyer profile, falling back to a minimal placeholder if Firebase fails or the
 * profile doesn't exist yet. Used by checkout / update-order / merge paths that require
 * a buyerProfile snapshot but should not abort if the read errors.
 */
internal suspend fun BuyAppViewModel.getBuyerProfileOrFallback(currentUserId: String): BuyerProfile {
    val profile = try {
        profileRepository.getBuyerProfile().getOrNull()
    } catch (_: Exception) {
        null
    }
    return profile ?: BuyerProfile(
        id = currentUserId,
        displayName = "Kunde",
        emailAddress = "",
        anonymous = false
    )
}

internal fun BuyAppViewModel.handleBasketScreenAction(action: BuyBasketScreenAction) {
    when (action) {
        is BuyBasketScreenAction.AddItem -> basketScreenAddItem(action.item)
        is BuyBasketScreenAction.RemoveItem -> basketScreenRemoveItem(action.productId)
        is BuyBasketScreenAction.UpdateItemQuantity -> basketScreenUpdateQuantity(action.productId, action.newQuantity)
        BuyBasketScreenAction.ClearBasket -> basketScreenClearBasket()
        BuyBasketScreenAction.Checkout -> basketScreenRequestCheckout()
        BuyBasketScreenAction.ConfirmCheckout -> basketScreenCheckout()
        BuyBasketScreenAction.DismissCheckout -> basketScreenDismissCheckout()
        is BuyBasketScreenAction.SetFulfilment -> basketScreenSetFulfilment(action.fulfilment)
        is BuyBasketScreenAction.SetDeliveryAddress ->
            basketScreenSetDeliveryAddress(action.address)
        BuyBasketScreenAction.SaveDeliveryAddressToProfile ->
            basketScreenSaveDeliveryAddressToProfile()
        is BuyBasketScreenAction.LoadOrder -> basketScreenLoadOrder(action.orderId, action.date, forceLoad = true)
        BuyBasketScreenAction.UpdateOrder -> basketScreenUpdateOrder()
        BuyBasketScreenAction.EnableEditing -> basketScreenEnableEditing()
        BuyBasketScreenAction.DisableEditing -> _state.update { it.copy(basketScreen = it.basketScreen.copy(isEditMode = false)) }
        BuyBasketScreenAction.ResetOrderState -> basketScreenResetOrderState()
        BuyBasketScreenAction.ShowDatePicker -> basketScreenShowDatePicker()
        BuyBasketScreenAction.HideDatePicker -> basketScreenHideDatePicker()
        is BuyBasketScreenAction.SelectPickupDate -> basketScreenSelectPickupDate(action.date)
        BuyBasketScreenAction.LoadAvailableDates -> basketScreenLoadAvailableDates()
        BuyBasketScreenAction.ConfirmSlotPriceChange -> basketScreenConfirmSlotPriceChange()
        BuyBasketScreenAction.DismissSlotPriceChange -> basketScreenDismissSlotPriceChange()
        BuyBasketScreenAction.CancelOrder -> basketScreenCancelOrder()
        BuyBasketScreenAction.ShowReorderDatePicker -> basketScreenShowReorderDatePicker()
        BuyBasketScreenAction.HideReorderDatePicker -> basketScreenHideReorderDatePicker()
        is BuyBasketScreenAction.ReorderWithNewDate -> basketScreenReorderWithNewDate(action.newPickupDate, action.currentArticles)
        BuyBasketScreenAction.HideMergeDialog -> basketScreenHideMergeDialog()
        is BuyBasketScreenAction.ResolveMergeConflict -> basketScreenResolveMergeConflict(action.productId, action.resolution)
        BuyBasketScreenAction.ConfirmMerge -> basketScreenConfirmMerge()
        BuyBasketScreenAction.HideDraftWarningDialog -> basketScreenHideDraftWarningDialog()
        BuyBasketScreenAction.SaveDraftAndLoadOrder -> basketScreenSaveDraftAndLoadOrder()
        BuyBasketScreenAction.DiscardDraftAndLoadOrder -> basketScreenDiscardDraftAndLoadOrder()
        BuyBasketScreenAction.ShowCancelConfirmDialog -> _state.update { it.copy(basketScreen = it.basketScreen.copy(showCancelConfirmDialog = true)) }
        BuyBasketScreenAction.HideCancelConfirmDialog -> _state.update { it.copy(basketScreen = it.basketScreen.copy(showCancelConfirmDialog = false)) }
    }
}

/**
 * Initialize basket screen observers
 * Called from init block
 */
internal fun BuyAppViewModel.initializeBasketScreen() {
    observeBasketScreenItems()
    basketScreenLoadAvailableDates()
    basketScreenLoadMostRecentEditableOrder()
}

internal fun BuyAppViewModel.observeBasketScreenItems() {
    viewModelScope.launch {
        basketRepository.observeBasket().collect { items ->
            val hasChanges = basketScreenCheckIfHasChanges(items, _state.value.basketScreen.originalOrderItems)
            _state.update { current ->
                current.copy(
                    basketScreen = current.basketScreen.copy(
                        items = items,
                        total = basketRepository.getTotal(),
                        hasChanges = hasChanges,
                        orderSuccess = false // Hide success message on any change
                    )
                )
            }

            // Auto-save draft basket with debouncing (only for new drafts, not loaded orders)
            if (basketRepository.hasDraftBasket()) {
                scheduleDraftBasketSave()
            }
        }
    }
}

/**
 * Schedule a debounced save of the draft basket to profile.
 * Cancels any pending save and schedules a new one after the debounce delay.
 */
private fun BuyAppViewModel.scheduleDraftBasketSave() {
    draftSaveJob?.cancel()
    draftSaveJob = viewModelScope.launch {
        delay(DRAFT_SAVE_DEBOUNCE_MS)
        saveDraftBasketToProfile()
    }
}



/**
 * Save the current draft basket to the user's profile.
 * Only saves if there are items and it's a draft (not loaded from an existing order).
 */
internal suspend fun BuyAppViewModel.saveDraftBasketToProfile() {
    if (!basketRepository.hasDraftBasket()) {
        bLog("🛒 saveDraftBasketToProfile: Skipping - not a draft basket")
        return
    }

    if (_state.value.isDemoMode) {
        bLog("🛒 saveDraftBasketToProfile: Skipping - demo mode, no Firebase write")
        return
    }

    val selectedDateTimestamp = _state.value.basketScreen.selectedPickupDate
    val selectedDateKey = selectedDateTimestamp?.let { basketScreenFormatDateKey(it) }
    val draftBasket = basketRepository.toDraftBasket(
        selectedPickupDate = selectedDateKey,
        selectedMarketId = _state.value.basketScreen.selectedMarketId
    )

    if (draftBasket.items.isEmpty()) {
        // Clear draft basket if empty
        bLog("🛒 saveDraftBasketToProfile: Basket empty, clearing draft")
        profileRepository.clearDraftBasket()
        return
    }

    bLog("🛒 saveDraftBasketToProfile: Saving ${draftBasket.items.size} items to profile")
    profileRepository.saveDraftBasket(draftBasket).fold(
        onSuccess = {
            bLog("✅ saveDraftBasketToProfile: Draft basket saved")
        },
        onFailure = { error ->
            bLog("❌ saveDraftBasketToProfile: Failed - ${error.message}")
        }
    )
}

internal fun basketScreenCheckIfHasChanges(
    currentItems: List<OrderedProduct>,
    originalItems: List<OrderedProduct>
): Boolean {
    if (currentItems.size != originalItems.size) return true
    if (currentItems.isEmpty()) return false
    val originalByProduct = originalItems.associateBy { it.productId }
    if (originalByProduct.size != originalItems.size) return true // duplicate productIds → treat as changed
    return currentItems.any { item ->
        val original = originalByProduct[item.productId] ?: return@any true
        original.amountCount != item.amountCount
    }
}

internal fun BuyAppViewModel.basketScreenLoadMostRecentEditableOrder() {
    viewModelScope.launch {
        try {
            bLog("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: START")

            val loadedOrderInfo = basketRepository.getLoadedOrderInfo()
            if (loadedOrderInfo != null) {
                val (orderId, orderDate) = loadedOrderInfo
                bLog("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Order already loaded - orderId=$orderId, date=$orderDate")

                val isDemo = _state.value.isDemoMode
                val result = if (isDemo) {
                    val demoOrder = sellerConfig.loadDemoOrders().find { it.id == orderId }
                    if (demoOrder != null) {
                        Result.success(demoOrder)
                    } else {
                        val orderPath = "demo_orders/${sellerConfig.sellerId}/$orderDate/$orderId"
                        orderRepository.loadOrder(sellerConfig.sellerId, orderId, orderPath)
                    }
                } else {
                    val orderPath = "orders/${sellerConfig.sellerId}/$orderDate/$orderId"
                    orderRepository.loadOrder(sellerConfig.sellerId, orderId, orderPath)
                }
                result.onSuccess { loadedOrder ->
                    // Check if order is finalized
                    // Finished orders stay as the seller or buyer left them; in particular a
                    // NOT_PICKED_UP must not be overwritten with COMPLETED further down.
                    if (loadedOrder.status == com.together.newverse.domain.model.OrderStatus.CANCELLED ||
                        loadedOrder.status == com.together.newverse.domain.model.OrderStatus.COMPLETED ||
                        loadedOrder.status == com.together.newverse.domain.model.OrderStatus.NOT_PICKED_UP) {
                        bLog("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Order is finalized, clearing basket")
                        basketRepository.clearBasket()
                        // Clear basket state
                        _state.update { current ->
                            current.copy(
                                basket = current.basket.copy(
                                    currentOrderId = null,
                                    currentOrderDate = null
                                )
                            )
                        }
                        return@onSuccess
                    }

                    // Check if pickup date has passed
                    val now = Clock.System.now()
                    val pickupInstant = Instant.fromEpochMilliseconds(loadedOrder.pickUpDate)
                    if (now > pickupInstant) {
                        bLog("⏰ BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Pickup date has passed, transitioning to COMPLETED and clearing basket")
                        // Update Firebase with COMPLETED status
                        orderRepository.updateOrderStatus(sellerConfig.sellerId, orderDate, orderId, com.together.newverse.domain.model.OrderStatus.COMPLETED, isDemo = isDemo)

                        basketRepository.clearBasket()
                        // Clear basket state
                        _state.update { current ->
                            current.copy(
                                basket = current.basket.copy(
                                    currentOrderId = null,
                                    currentOrderDate = null
                                )
                            )
                        }
                        return@onSuccess
                    }

                    // Apply status transition if needed (PLACED->LOCKED)
                    val order = loadedOrder.transitionStatusIfNeeded()?.let { updatedOrder ->
                        bLog("🔄 basketScreenLoadMostRecentEditableOrder: Status transition ${loadedOrder.status} -> ${updatedOrder.status}")
                        // Update Firebase with new status
                        orderRepository.updateOrderStatus(sellerConfig.sellerId, orderDate, orderId, updatedOrder.status, isDemo = isDemo)

                        // If transitioned to COMPLETED, clear basket
                        if (updatedOrder.status == com.together.newverse.domain.model.OrderStatus.COMPLETED) {
                            bLog("⏰ BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Order transitioned to COMPLETED, clearing basket")
                            basketRepository.clearBasket()
                            // Clear basket state
                            _state.update { current ->
                                current.copy(
                                    basket = current.basket.copy(
                                        currentOrderId = null,
                                        currentOrderDate = null
                                    )
                                )
                            }
                            return@onSuccess
                        }
                        updatedOrder
                    } ?: loadedOrder

                    val canEdit = order.canEdit()
                    val currentBasketItems = basketRepository.observeBasket().value
                    val hasChanges = basketScreenCheckIfHasChanges(currentBasketItems, order.articles)

                    _state.update { current ->
                        current.copy(
                            basketScreen = current.basketScreen.copy(
                                orderId = orderId,
                                orderDate = orderDate,
                                pickupDate = order.pickUpDate,
                                createdDate = order.createdDate,
                                // From the order, not the profile: editing a delivery
                                // must not quietly turn it back into a pickup because
                                // the profile says self-pickup.
                                fulfilment = order.fulfilment,
                                deliveryAddress = order.deliveryAddress,
                                isEditMode = false,
                                canEdit = canEdit,
                                originalOrderItems = order.articles,
                                hasChanges = hasChanges
                            )
                        )
                    }
                }.onFailure { error ->
                    bLog("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Failed to load order - ${error.message}")
                    // Clear loaded order info since loading failed
                    basketRepository.clearBasket()
                    // Clear basket state
                    _state.update { current ->
                        current.copy(
                            basket = current.basket.copy(
                                currentOrderId = null,
                                currentOrderDate = null
                            )
                        )
                    }
                }
                return@launch
            }

            // sellerConfig.isDemoMode is always true because demoSellerId == real sellerId,
            // so use state.isDemoMode to distinguish demo (local) from real (Firebase) orders.
            val isDemo = _state.value.isDemoMode

            // In demo mode, find the most recent editable order
            if (isDemo) {
                // 1. Try local storage first (post-migration orders)
                val demoOrder = sellerConfig.loadDemoOrders()
                    .filter { it.canEdit() }
                    .maxByOrNull { it.createdDate }

                if (demoOrder != null) {
                    val dateKey = basketScreenFormatDateKey(demoOrder.pickUpDate)
                    basketScreenLoadOrder(demoOrder.id, dateKey)
                    return@launch
                }

                // 2. Fall back to Firebase demo_orders (pre-migration orders)
                bLog("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: No local demo order, checking Firebase")
                val profileResult = profileRepository.getBuyerProfile()
                val buyerProfile = profileResult.getOrNull()

                if (buyerProfile != null && buyerProfile.placedOrderIds.isNotEmpty()) {
                    val orderResult = orderRepository.getOpenEditableOrder(
                        sellerConfig.sellerId,
                        buyerProfile.placedOrderIds,
                        isDemo = true
                    )
                    val firebaseOrder = orderResult.getOrNull()
                    if (firebaseOrder != null) {
                        val dateKey = basketScreenFormatDateKey(firebaseOrder.pickUpDate)
                        basketScreenLoadOrder(firebaseOrder.id, dateKey)
                        return@launch
                    }
                }

                bLog("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: No editable demo orders found anywhere")
                return@launch
            }

            val profileResult = profileRepository.getBuyerProfile()
            val buyerProfile = profileResult.getOrNull()

            if (buyerProfile == null || buyerProfile.placedOrderIds.isEmpty()) {
                bLog("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: No buyer profile or orders")
                return@launch
            }

            val orderResult = orderRepository.getOpenEditableOrder(sellerConfig.sellerId, buyerProfile.placedOrderIds, isDemo = isDemo)
            val order = orderResult.getOrNull()

            if (order != null) {
                val dateKey = basketScreenFormatDateKey(order.pickUpDate)
                basketScreenLoadOrder(order.id, dateKey)
            }
        } catch (e: Exception) {
            bLog("❌ BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Error - ${e.message}")
        }
    }
}

internal fun BuyAppViewModel.basketScreenAddItem(item: OrderedProduct) {
    viewModelScope.launch {
        basketRepository.addItem(item)
    }
}

internal fun BuyAppViewModel.basketScreenRemoveItem(productId: String) {
    viewModelScope.launch {
        basketRepository.removeItem(productId)

        val basketState = _state.value.basketScreen
        val isPlacedOrder = basketState.orderId != null
        val isNowEmpty = basketRepository.observeBasket().value.isEmpty()

        when {
            isPlacedOrder && isNowEmpty -> {
                bLog("🛒 basketScreenRemoveItem: Last item removed from placed order, resetting to empty draft.")
                basketScreenResetToEmptyDraft()
            }
            !isPlacedOrder && isNowEmpty -> {
                bLog("🛒 basketScreenRemoveItem: Last item removed from draft basket, clearing draft immediately.")
                draftSaveJob?.cancel()
                if (!_state.value.isDemoMode) {
                    try {
                        profileRepository.clearDraftBasket()
                    } catch (e: Exception) {
                        bLog("⚠️ basketScreenRemoveItem: Failed to clear draft from profile: ${e.message}")
                    }
                }
                basketRepository.clearBasket()
            }
        }
    }
}

internal fun BuyAppViewModel.basketScreenUpdateQuantity(productId: String, newQuantity: Double) {
    viewModelScope.launch {
        basketRepository.updateQuantity(productId, newQuantity)
    }
}

/**
 * Resets the basket to a clean, empty draft state.
 * This is used when a placed order becomes empty, effectively cancelling it.
 */
private fun BuyAppViewModel.basketScreenResetToEmptyDraft() {
    viewModelScope.launch {
        val basketState = _state.value.basketScreen
        val orderId = basketState.orderId
        val orderDate = basketState.orderDate

        // If it was a real order, remove it from the backend
        if (orderId != null && orderDate != null) {
            val isDemo = _state.value.isDemoMode
            val result = if (isDemo) {
                if (orderId.startsWith("demo_")) {
                    runCatching { sellerConfig.removeDemoOrder(orderId) }.map { true }
                } else {
                    orderRepository.cancelOrder(sellerConfig.sellerId, orderDate, orderId, isDemo = true)
                }
            } else {
                orderRepository.cancelOrder(sellerConfig.sellerId, orderDate, orderId, isDemo = false)
            }

            if (result.isSuccess) {
                removePlacedOrderIdReference(orderId)
                bLog("✅ basketScreenResetToEmptyDraft: Placed order $orderId cancelled successfully.")
            } else {
                bLog("❌ basketScreenResetToEmptyDraft: Failed to cancel placed order $orderId: ${result.exceptionOrNull()?.message}")
                // Don't block UI reset even if backend fails. The order will be orphaned but user can proceed.
            }
        }

        // Clear the local basket and reset the UI state
        basketRepository.clearBasket()
        val availableDates = _state.value.basketScreen.availablePickupDates
        _state.update { current ->
            current.copy(
                basket = current.basket.copy(
                    currentOrderId = null,
                    currentOrderDate = null
                ),
                basketScreen = BasketScreenState(
                    availablePickupDates = availableDates
                )
            )
        }
        bLog("🔄 basketScreenResetToEmptyDraft: Basket state has been reset to a new empty draft.")
    }
}

internal fun BuyAppViewModel.basketScreenLoadHistoryOrderAsReorder(order: Order) {
    viewModelScope.launch {
        val currentUserId = authRepository.getCurrentUserId() ?: return@launch
        val freshProfile = getBuyerProfileOrFallback(currentUserId)
        val currentArticles = _state.value.mainScreen.articles

        val draftOrder = order.prepareForReorder(
            freshBuyerProfile = freshProfile,
            origin = demoOrProduction()
        )

        // Withdrawn articles are left behind rather than carried forward: keeping
        // one only builds an order the seller has to refuse, and the buyer finds out
        // at checkout instead of here, where they can still choose something else.
        val dropped = draftOrder.articles.unorderableAgainst(currentArticles)
        val correctedArticles = draftOrder.articles
            .orderableOnly(currentArticles)
            .map { item ->
                val article = currentArticles.find { it.id == item.articleKey() }
                if (article != null) {
                    item.copy(price = article.price, productName = article.productName, unit = article.unit)
                } else item
            }

        if (!_state.value.isDemoMode) {
            try { profileRepository.clearDraftBasket() } catch (_: Exception) {}
        }
        basketRepository.clearBasket()
        for (item in correctedArticles) { basketRepository.addItem(item) }

        val availableDates = _state.value.basketScreen.availablePickupDates
        _state.update { current ->
            current.copy(
                basket = current.basket.copy(
                    currentOrderId = null,
                    currentOrderDate = null
                ),
                basketScreen = BasketScreenState(
                    items = correctedArticles,
                    total = correctedArticles.sumOf { it.price * it.amountCount },
                    availablePickupDates = availableDates,
                    canEdit = true,
                    // Said here rather than discovered at checkout: the buyer is
                    // looking at the basket they just asked for.
                    orderError = if (dropped.isEmpty()) null else droppedFromReorderMessage(dropped)
                ),
                navigateToBasketAsTopLevel = true
            )
        }
        bLog("🛒 basketScreenLoadHistoryOrderAsReorder: Loaded ${correctedArticles.size} articles as new draft")
    }
}

internal fun BuyAppViewModel.basketScreenClearBasket() {
    viewModelScope.launch {
        basketRepository.clearBasket()
    }
}

/**
 * Pressed Bestellen: check the order is placeable, then ask the buyer to confirm
 * where and when rather than writing it straight away.
 *
 * Only the two checks that decide whether a confirmation is worth showing at all
 * happen here — an empty basket and a missing date, the latter opening the picker
 * instead. Everything else, including the deadline and whether the date is still
 * open, is re-checked in [basketScreenCheckout] at the moment of writing: the dialog
 * can sit open across a deadline, and validating only here would let a stale
 * confirmation through.
 *
 * `doc/guides/market-pricing-and-access-design.md` §4.
 */
internal fun BuyAppViewModel.basketScreenRequestCheckout() {
    val basket = _state.value.basketScreen

    if (basket.items.isEmpty()) {
        setBasketError("Warenkorb ist leer")
        return
    }

    if (basket.selectedPickupDate == null) {
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(
                    orderError = "Bitte wählen Sie ein Abholdatum",
                    showDatePicker = true
                )
            )
        }
        return
    }

    if (refuseImpossibleDelivery()) return

    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                showCheckoutConfirm = true,
                orderError = null
            )
        )
    }
}

/**
 * A delivery that cannot happen, refused: nowhere to deliver to, or a stall that does
 * not deliver at all.
 *
 * The seller cannot read `buyer_profile`, so an order with no address is an order
 * with no destination and no way to recover one — they would have to chase the buyer
 * by hand, if they noticed at all. A delivery against a pickup-only market is the same
 * kind of dead end from the other side: the seller never goes there with a van, and
 * `database.rules.json` refuses the write regardless.
 *
 * Checked at every point an order is *written*, not only when the confirmation opens:
 * the fulfilment chips live inside that dialog, so a buyer who opens it on a pickup
 * and switches to delivery there passes the opening gate and reaches the write with a
 * blank address. The dialog also keeps its confirm button disabled in that state;
 * this is the line that holds when the disabled button is not the only way in — an
 * edit of a placed order, or a dialog left open while the address was cleared.
 *
 * Returns true when it refused, leaving the dialog open on the error rather than
 * closing on a silent failure. `isCheckingOut` is cleared too: the write paths set it
 * before validating.
 */
internal fun BuyAppViewModel.refuseImpossibleDelivery(): Boolean {
    val basket = _state.value.basketScreen
    if (basket.fulfilment != Fulfilment.DELIVERY) return false

    // A stall that does not deliver. The chips do not offer it and the slot settles it
    // (see basketScreenApplySlot), but a market can be switched to pickup-only while a
    // buyer sits in the basket, and the database rules refuse such an order anyway —
    // better a stated reason here than a rejected write with none.
    val market = _state.value.eligibleMarkets
        .firstOrNull { it.id == basket.selectedMarketId }
    val reason = when {
        market?.allowsDelivery == false ->
            "${market.name.ifBlank { "Dieser Markt" }} liefert nicht — bitte Abholung wählen"
        !basket.deliveryAddress.isComplete -> "Bitte Lieferadresse angeben"
        else -> return false
    }

    bLog("🛒 refuseImpossibleDelivery: $reason")
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                isCheckingOut = false,
                showCheckoutConfirm = true,
                orderError = reason
            )
        )
    }
    return true
}

/**
 * Keep the address typed at checkout, on the buyer asking to.
 *
 * Only street and number, which is all `BuyerProfile` holds and all
 * [DeliveryAddress.isComplete] requires; the note stays on the order. An incomplete
 * address is not written at all — half an address must not overwrite a whole one.
 */
internal fun BuyAppViewModel.basketScreenSaveDeliveryAddressToProfile() {
    viewModelScope.launch {
        val address = _state.value.basketScreen.deliveryAddress
        if (!address.isComplete) {
            bLog("🛒 basketScreenSaveDeliveryAddressToProfile: refused, address incomplete")
            dispatch(BuyUiAction.ShowSnackbar("Bitte Lieferadresse angeben"))
            return@launch
        }
        val current = _state.value.customerProfile.profile
        if (current == null) {
            dispatch(BuyUiAction.ShowSnackbar("Fehler: Kein Profil vorhanden"))
            return@launch
        }
        profileRepository.saveBuyerProfile(
            current.copy(street = address.street, houseNumber = address.houseNumber)
        ).onSuccess { saved ->
            _state.update { state ->
                state.copy(
                    customerProfile = state.customerProfile.copy(profile = saved)
                )
            }
            dispatch(BuyUiAction.ShowSnackbar("Adresse im Profil gespeichert"))
        }.onFailure { error ->
            bLog("⚠️ basketScreenSaveDeliveryAddressToProfile: ${error.message}")
            dispatch(BuyUiAction.ShowSnackbar("Fehler beim Speichern: ${error.message}"))
        }
    }
}

/**
 * Pickup or delivery for this order.
 *
 * Switching to delivery seeds the address from the profile if nothing has been typed
 * yet, so the common case — a buyer whose profile already holds their address — is one
 * tap rather than retyping what the app knows.
 */
internal fun BuyAppViewModel.basketScreenSetFulfilment(requested: Fulfilment) {
    // The stall has the last word, whatever reaches this action: the chips are built
    // from the same rule, so this only bites if the market changed under them.
    val market = _state.value.eligibleMarkets
        .firstOrNull { it.id == _state.value.basketScreen.selectedMarketId }
    val fulfilment = Fulfilment.forMarket(market, requested)

    _state.update { current ->
        val seeded = if (fulfilment == Fulfilment.DELIVERY &&
            current.basketScreen.deliveryAddress.isEmpty
        ) {
            DeliveryAddress.fromProfile(current.customerProfile.profile)
        } else {
            current.basketScreen.deliveryAddress
        }
        current.copy(
            basketScreen = current.basketScreen.copy(
                fulfilment = fulfilment,
                deliveryAddress = seeded,
                orderError = null
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenSetDeliveryAddress(address: DeliveryAddress) {
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                deliveryAddress = address,
                orderError = null
            )
        )
    }
}

/** Backed out. Nothing was written and the basket keeps its slot. */
internal fun BuyAppViewModel.basketScreenDismissCheckout() {
    _state.update { current ->
        current.copy(basketScreen = current.basketScreen.copy(showCheckoutConfirm = false))
    }
}

internal fun BuyAppViewModel.basketScreenCheckout() {
    viewModelScope.launch {
        bLog("🛒 BuyAppViewModel.basketScreenCheckout: START")
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(
                    isCheckingOut = true,
                    showCheckoutConfirm = false,
                    orderSuccess = false,
                    orderError = null
                )
            )
        }

        try {
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                setBasketError("Bitte melden Sie sich an, um eine Bestellung aufzugeben")
                return@launch
            }

            // Check if buyer is blocked by seller
            val isBlocked = profileRepository.getAccessStatus(sellerConfig.sellerId) == AccessStatus.BLOCKED
            if (isBlocked) {
                setBasketError("You have been blocked by this seller")
                return@launch
            }

            val items = _state.value.basketScreen.items
            if (items.isEmpty()) {
                setBasketError("Warenkorb ist leer")
                return@launch
            }

            // An order the seller would only have to refuse is not placed. The
            // catalogue filter cannot carry this: the basket may have been sitting
            // here since before the article was withdrawn.
            val unorderable = unorderableInBasket(items)
            if (unorderable.isNotEmpty()) {
                bLog("🛒 basketScreenCheckout: refused, unorderable=${unorderable.map { it.articleId }}")
                setBasketError(unorderableBasketMessage(unorderable))
                return@launch
            }

            // Re-checked here, not only when the confirmation opened: the fulfilment
            // chips are inside that dialog, so this is where a delivery chosen there
            // is first seen by a write.
            if (refuseImpossibleDelivery()) return@launch

            val buyerProfile = getBuyerProfileOrFallback(currentUserId)

            val selectedDate = _state.value.basketScreen.selectedPickupDate
            if (selectedDate == null) {
                _state.update { current ->
                    current.copy(
                        basketScreen = current.basketScreen.copy(
                            isCheckingOut = false,
                            orderError = "Bitte wählen Sie ein Abholdatum",
                            showDatePicker = true
                        )
                    )
                }
                return@launch
            }

            val isDateValid = OrderDateUtils.isPickupDateValid(
                Instant.fromEpochMilliseconds(selectedDate),
                closures = _state.value.sellerClosures
            )
            if (!isDateValid) {
                _state.update { current ->
                    current.copy(
                        basketScreen = current.basketScreen.copy(
                            isCheckingOut = false,
                            orderError = "Gewähltes Datum ist nicht mehr verfügbar.",
                            selectedPickupDate = null,
                            showDatePicker = true
                        )
                    )
                }
                basketScreenLoadAvailableDates()
                return@launch
            }

            // Check if there's an existing order for this pickup date - merge if so
            val dateKey = basketScreenFormatDateKey(selectedDate)
            val isDemo = _state.value.isDemoMode

            val existingOrder = if (isDemo) {
                // 1. Check local demo orders
                val localMatch = sellerConfig.loadDemoOrders().find { basketScreenFormatDateKey(it.pickUpDate) == dateKey }
                if (localMatch != null) {
                    localMatch
                } else {
                    // 2. Check Firebase demo orders
                    val firebaseOrderId = buyerProfile.placedOrderIds[dateKey]
                    if (firebaseOrderId != null) {
                        val path = "demo_orders/${sellerConfig.sellerId}/$dateKey/$firebaseOrderId"
                        orderRepository.loadOrder(sellerConfig.sellerId, firebaseOrderId, path).getOrNull()
                    } else null
                }
            } else {
                // Production: check Firebase orders/
                val firebaseOrderId = buyerProfile.placedOrderIds[dateKey]
                if (firebaseOrderId != null) {
                    val path = "orders/${sellerConfig.sellerId}/$dateKey/$firebaseOrderId"
                    orderRepository.loadOrder(sellerConfig.sellerId, firebaseOrderId, path).getOrNull()
                } else null
            }

            if (existingOrder != null) {
                if (!existingOrder.canEdit()) {
                    setBasketError("Bestehende Bestellung für diesen Termin kann nicht mehr bearbeitet werden (Frist abgelaufen)")
                    return@launch
                }

                val conflicts = basketScreenCalculateMergeConflicts(items, existingOrder.articles)
                _state.update { current ->
                    current.copy(
                        basketScreen = current.basketScreen.copy(
                            isCheckingOut = false,
                            showMergeDialog = true,
                            existingOrderForMerge = existingOrder,
                            mergeConflicts = conflicts
                        )
                    )
                }
                return@launch
            }

            // Where this order is collected: the market named by the slot the buyer
            // picked, falling back to their contextual market while a single-market
            // seller has nothing to choose. The rules accept empty, and reject an id
            // that names no market.
            val orderMarketId = _state.value.basketScreen.selectedMarketId
                .ifEmpty { _state.value.contextualMarketId.orEmpty() }

            val order = Order(
                buyerProfile = buyerProfile,
                createdDate = Clock.System.now().toEpochMilliseconds(),
                sellerId = sellerConfig.sellerId,
                marketId = orderMarketId,
                // Snapshotted, not looked up: the seller cannot read buyer_profile, and
                // a buyer who moves house afterwards must not redirect an order that is
                // already on its way.
                fulfilment = _state.value.basketScreen.fulfilment,
                deliveryAddress = if (_state.value.basketScreen.fulfilment == Fulfilment.DELIVERY) {
                    _state.value.basketScreen.deliveryAddress
                } else DeliveryAddress.NONE,
                // Snapshotted beside the prices it produced: the seller may edit
                // conditions tomorrow, and this is what says the articles above were
                // priced against yesterday's.
                pricingRevision = _state.value.pricingRevisionOf(orderMarketId),
                pickUpDate = selectedDate,
                message = "",
                articles = items,
                origin = demoOrProduction(),
                status = com.together.newverse.domain.model.OrderStatus.PLACED
            )

            // Demo mode routing:
            //   Orders 1 & 2 → Firebase demo_orders/ (for seller observability)
            //   Order 3       → migration: pull 1 & 2 from Firebase to local, delete from Firebase
            //   Orders 4+     → local-only (already in local mode post-migration)
            var handledByMigration = false
            val result = if (!isDemo) {
                orderRepository.placeOrder(order)
            } else if (sellerConfig.firebaseDemoWritesRemaining() > 0) {
                orderRepository.placeOrder(order).also { r ->
                    r.onSuccess { sellerConfig.recordFirebaseDemoWrite() }
                }
            } else if (sellerConfig.isDemoLocalMode()) {
                // Post-migration: local-only
                Result.success(order.copy(id = "demo_${Clock.System.now().toEpochMilliseconds()}"))
            } else {
                // 3rd order: trigger atomic migration
                handledByMigration = true
                performDemoMigration(order)
            }
            result.onSuccess { placedOrder ->
                if (isDemo && !handledByMigration) {
                    sellerConfig.saveDemoOrder(placedOrder)
                    loadOrderHistory()
                }
                // Clear draft basket — skip Firebase profile write in demo mode
                try {
                    if (!isDemo) {
                        profileRepository.clearDraftBasket()
                    }
                    basketRepository.clearBasket()
                    bLog("🛒 basketScreenCheckout: Cleared draft basket after order placed")
                } catch (e: Exception) {
                    bLog("⚠️ basketScreenCheckout: Failed to clear draft basket: ${e.message}")
                }

                val placedDateKey = basketScreenFormatDateKey(placedOrder.pickUpDate)
                basketScreenLoadOrder(placedOrder.id, placedDateKey, forceLoad = true)

                _state.update { current ->
                    current.copy(
                        basketScreen = current.basketScreen.copy(
                            isCheckingOut = false,
                            orderSuccess = true
                        )
                    )
                }
            }.onFailure { error ->
                setBasketError(error.message ?: "Bestellung fehlgeschlagen")
            }
        } catch (e: Exception) {
            setBasketError(e.message ?: "Ein Fehler ist aufgetreten")
        }
    }
}

internal fun BuyAppViewModel.basketScreenResetOrderState() {
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                orderSuccess = false,
                orderError = null
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenLoadOrder(orderId: String, date: String, forceLoad: Boolean = false) {
    viewModelScope.launch {
        bLog("🛒 BuyAppViewModel.basketScreenLoadOrder: START - orderId=$orderId, date=$date, forceLoad=$forceLoad")

        // Check for unsaved draft basket (only if not force loading after dialog confirmation)
        if (!forceLoad && basketScreenShowDraftWarningIfNeeded(orderId, date)) {
            bLog("🛒 BuyAppViewModel.basketScreenLoadOrder: Draft warning dialog shown, waiting for user decision")
            return@launch
        }

        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(
                    isLoadingOrder = true,
                    orderError = null
                )
            )
        }

        try {
            val isDemo = _state.value.isDemoMode
            val result = if (isDemo) {
                // Try local first
                val localOrder = sellerConfig.loadDemoOrders().find { it.id == orderId }
                if (localOrder != null) {
                    Result.success(localOrder)
                } else {
                    // Fall back to Firebase demo_orders
                    val orderPath = "demo_orders/${sellerConfig.sellerId}/$date/$orderId"
                    orderRepository.loadOrder(sellerConfig.sellerId, orderId, orderPath)
                }
            } else {
                val orderPath = "orders/${sellerConfig.sellerId}/$date/$orderId"
                orderRepository.loadOrder(sellerConfig.sellerId, orderId, orderPath)
            }

            result.onSuccess { loadedOrder ->
                // Apply status transition if needed (PLACED->LOCKED or LOCKED->COMPLETED)
                val order = loadedOrder.transitionStatusIfNeeded()?.let { updatedOrder ->
                    bLog("🔄 basketScreenLoadOrder: Status transition ${loadedOrder.status} -> ${updatedOrder.status}")
                    // Update Firebase with new status
                    viewModelScope.launch {
                        orderRepository.updateOrderStatus(sellerConfig.sellerId, date, orderId, updatedOrder.status, isDemo = isDemo)
                    }
                    updatedOrder
                } ?: loadedOrder

                val canEdit = order.canEdit()
                val currentBasketItems = basketRepository.observeBasket().value

                val shouldLoadOrderItems = forceLoad ||
                    currentBasketItems.isEmpty() ||
                    basketRepository.getLoadedOrderInfo()?.first != orderId

                if (shouldLoadOrderItems) {
                    basketRepository.loadOrderItems(order.articles, orderId, date)
                }

                val finalBasketItems = basketRepository.observeBasket().value
                val hasChanges = basketScreenCheckIfHasChanges(finalBasketItems, order.articles)

                _state.update { current ->
                    current.copy(
                        basketScreen = current.basketScreen.copy(
                            orderId = orderId,
                            orderDate = date,
                            pickupDate = order.pickUpDate,
                            createdDate = order.createdDate,
                            fulfilment = order.fulfilment,
                            deliveryAddress = order.deliveryAddress,
                            isEditMode = false,
                            canEdit = canEdit,
                            isLoadingOrder = false,
                            items = finalBasketItems,
                            total = finalBasketItems.sumOf { it.price * it.amountCount },
                            originalOrderItems = order.articles,
                            hasChanges = hasChanges
                        )
                    )
                }
            }.onFailure { error ->
                // Clear loaded order info since loading failed
                basketRepository.clearBasket()
                _state.update { current ->
                    current.copy(
                        basketScreen = BasketScreenState(
                            isLoadingOrder = false,
                            orderError = "Bestellung konnte nicht geladen werden: ${error.message}",
                            availablePickupDates = current.basketScreen.availablePickupDates
                        )
                    )
                }
            }
        } catch (e: Exception) {
            basketRepository.clearBasket()
            _state.update { current ->
                current.copy(
                    basketScreen = BasketScreenState(
                        isLoadingOrder = false,
                        orderError = "Fehler beim Laden der Bestellung: ${e.message}",
                        availablePickupDates = current.basketScreen.availablePickupDates
                    )
                )
            }
        }
    }
}

internal fun BuyAppViewModel.basketScreenEnableEditing() {
    val basket = _state.value.basketScreen
    val pickupDate = basket.pickupDate

    // `canEdit` defaults to false and is only given a real value once an order has
    // finished loading, so false on its own means "not known yet" as often as it means
    // "too late". Refusing with a deadline message before anything is loaded told the
    // buyer their deadline had passed when it had not — seen on a device with the card
    // still reading "Bearbeitbar bis" four days out.
    if (basket.orderId == null || pickupDate == null) {
        bLog("🛒 BuyAppViewModel.basketScreenEnableEditing: no order loaded yet, ignoring")
        return
    }

    if (basket.canEdit) {
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(isEditMode = true)
            )
        }
    } else {
        setBasketError("Bestellung kann nicht mehr bearbeitet werden (Frist: ${editDeadlineText(pickupDate)})")
    }
}

internal fun BuyAppViewModel.basketScreenUpdateOrder() {
    viewModelScope.launch {
        bLog("🛒 BuyAppViewModel.basketScreenUpdateOrder: START")
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(
                    isCheckingOut = true,
                    orderError = null
                )
            )
        }

        try {
            val basketState = _state.value.basketScreen
            val orderId = basketState.orderId
            val pickupDate = basketState.pickupDate
            val createdDate = basketState.createdDate

            if (orderId == null || pickupDate == null || createdDate == null) {
                setBasketError("Bestellinformationen fehlen")
                return@launch
            }

            val canEdit = OrderDateUtils.canEditOrder(Instant.fromEpochMilliseconds(pickupDate))
            if (!canEdit) {
                setBasketError("Bearbeitungsfrist abgelaufen (${editDeadlineText(pickupDate)})")
                return@launch
            }

            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                setBasketError("Benutzer nicht angemeldet")
                return@launch
            }

            val items = basketState.items
            if (items.isEmpty()) {
                setBasketError("Warenkorb ist leer")
                return@launch
            }

            // Editing an order is placing it again: an article withdrawn since it
            // was first placed cannot be kept in it either.
            val unorderable = unorderableInBasket(items)
            if (unorderable.isNotEmpty()) {
                bLog("🛒 basketScreenUpdateOrder: refused, unorderable=${unorderable.map { it.articleId }}")
                setBasketError(unorderableBasketMessage(unorderable))
                return@launch
            }

            // An edit is the path a placed pickup takes to become a delivery, so it
            // needs the destination just as much as placing one did.
            if (refuseImpossibleDelivery()) return@launch

            val buyerProfile = getBuyerProfileOrFallback(currentUserId)
            val isDemo = _state.value.isDemoMode
            val isLocalDemo = isDemo && orderId.startsWith("demo_")

            val editMarketId = basketState.selectedMarketId
                .ifEmpty { _state.value.contextualMarketId.orEmpty() }

            val updatedOrder = if (isLocalDemo) {
                Order(
                    id = orderId,
                    buyerProfile = buyerProfile,
                    createdDate = createdDate,
                    sellerId = sellerConfig.sellerId,
                    marketId = editMarketId,
                    pricingRevision = _state.value.pricingRevisionOf(editMarketId),
                    pickUpDate = pickupDate,
                    message = "",
                    articles = items,
                    origin = OrderOrigin.DEMO
                )
            } else {
                // Change the stored order, not a rebuilt one: updateOrder writes the
                // whole order, and a rebuilt one would drop what the buyer app does
                // not hold — the message, the market, and the seller having hidden it.
                val root = if (isDemo) "demo_orders" else "orders"
                val dateKey = basketState.orderDate
                    ?: OrderDateUtils.formatDateKey(Instant.fromEpochMilliseconds(pickupDate))
                val stored = orderRepository
                    .loadOrder(sellerConfig.sellerId, orderId, "$root/${sellerConfig.sellerId}/$dateKey/$orderId")
                    .getOrElse {
                        setBasketError("Bestellung konnte nicht geladen werden")
                        return@launch
                    }
                // The market moves only if the buyer actually chose one in this
                // session; otherwise the stored one stands, as it did before. Either
                // way marketId, pricingRevision and the article prices are written
                // from the same market, so the snapshot cannot contradict itself.
                val keptMarketId = basketState.selectedMarketId.ifEmpty { stored.marketId }
                stored.copy(
                    buyerProfile = buyerProfile,
                    articles = items,
                    // Re-snapshotted on an edit, which is the path a buyer takes to turn
                    // a placed pickup into a delivery. Changing their profile does not
                    // do it — see basketScreenFulfilmentDiffersFromProfile.
                    fulfilment = basketState.fulfilment,
                    deliveryAddress = if (basketState.fulfilment == Fulfilment.DELIVERY) {
                        basketState.deliveryAddress
                    } else DeliveryAddress.NONE,
                    marketId = keptMarketId,
                    pricingRevision = if (keptMarketId == stored.marketId) stored.pricingRevision
                    else _state.value.pricingRevisionOf(keptMarketId)
                )
            }

            val result = if (isDemo) {
                if (isLocalDemo) {
                    runCatching {
                        sellerConfig.updateDemoOrder(updatedOrder)
                        loadOrderHistory()
                    }
                } else {
                    // Firebase demo order
                    orderRepository.updateOrder(updatedOrder)
                }
            } else {
                orderRepository.updateOrder(updatedOrder)
            }
            result.onSuccess {
                val currentItems = _state.value.basketScreen.items
                _state.update { current ->
                    current.copy(
                        basketScreen = current.basketScreen.copy(
                            isCheckingOut = false,
                            orderSuccess = true,
                            isEditMode = false,
                            originalOrderItems = currentItems,
                            hasChanges = false
                        )
                    )
                }
            }.onFailure { error ->
                setBasketError(error.message ?: "Aktualisierung fehlgeschlagen")
            }
        } catch (e: Exception) {
            setBasketError(e.message ?: "Ein Fehler ist aufgetreten")
        }
    }
}

internal fun BuyAppViewModel.basketScreenCancelOrder() {
    viewModelScope.launch {
        bLog("🛒 BuyAppViewModel.basketScreenCancelOrder: START")
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(
                    isCancelling = true,
                    orderError = null,
                    cancelSuccess = false,
                    showCancelConfirmDialog = false
                )
            )
        }

        try {
            val basketState = _state.value.basketScreen
            val orderId = basketState.orderId
            val orderDate = basketState.orderDate
            val pickupDate = basketState.pickupDate

            if (orderId == null || orderDate == null || pickupDate == null) {
                setBasketError("Bestellinformationen fehlen")
                _state.update { it.copy(basketScreen = it.basketScreen.copy(isCancelling = false)) }
                return@launch
            }

            val canEdit = OrderDateUtils.canEditOrder(Instant.fromEpochMilliseconds(pickupDate))
            if (!canEdit) {
                setBasketError("Stornierung nicht mehr möglich (Frist: ${editDeadlineText(pickupDate)})")
                _state.update { it.copy(basketScreen = it.basketScreen.copy(isCancelling = false)) }
                return@launch
            }

            bLog("🛒 BuyAppViewModel.basketScreenCancelOrder: Calling cancelOrder")
            bLog("🛒 BuyAppViewModel.basketScreenCancelOrder: sellerId=${sellerConfig.sellerId}")
            bLog("🛒 BuyAppViewModel.basketScreenCancelOrder: orderDate=$orderDate")
            bLog("🛒 BuyAppViewModel.basketScreenCancelOrder: orderId=$orderId")
            val isDemo = _state.value.isDemoMode
            val result = if (isDemo) {
                if (orderId.startsWith("demo_")) {
                    runCatching { sellerConfig.removeDemoOrder(orderId) }.map { true }
                } else {
                    orderRepository.cancelOrder(sellerConfig.sellerId, orderDate, orderId, isDemo = true)
                }
            } else {
                orderRepository.cancelOrder(sellerConfig.sellerId, orderDate, orderId, isDemo = false)
            }

            if (result.isSuccess) {
                bLog("🛒 BuyAppViewModel.basketScreenCancelOrder: Cancel SUCCESS, clearing basket")
                // Clear basket in proper suspend context
                basketRepository.clearBasket()
                bLog("🛒 BuyAppViewModel.basketScreenCancelOrder: Basket cleared")

                removePlacedOrderIdReference(orderId)

                bLog("🛒 BuyAppViewModel.basketScreenCancelOrder: Updating state")
                val availableDates = _state.value.basketScreen.availablePickupDates
                _state.update { current ->
                    current.copy(
                        basket = current.basket.copy(
                            currentOrderId = null,
                            currentOrderDate = null
                        ),
                        basketScreen = BasketScreenState(
                            cancelSuccess = true,
                            availablePickupDates = availableDates,
                            isCancelling = false
                        )
                    )
                }
                bLog("🛒 BuyAppViewModel.basketScreenCancelOrder: State updated to empty basket with cancelSuccess=true")
            } else {
                val error = result.exceptionOrNull()
                val errorMessage = error?.message ?: "Stornierung fehlgeschlagen"
                bLog("🛒 BuyAppViewModel.basketScreenCancelOrder: Cancel FAILED - $errorMessage")

                // If order not found, the order was already cancelled/deleted - clear basket and show empty state
                if (errorMessage.contains("not found", ignoreCase = true)) {
                    bLog("🛒 BuyAppViewModel.basketScreenCancelOrder: Order not found, clearing basket")
                    basketRepository.clearBasket()

                    removePlacedOrderIdReference(orderId)

                    val availableDates = _state.value.basketScreen.availablePickupDates
                    _state.update { current ->
                        current.copy(
                            basket = current.basket.copy(
                                currentOrderId = null,
                                currentOrderDate = null
                            ),
                            basketScreen = BasketScreenState(
                                availablePickupDates = availableDates,
                                isCancelling = false
                            )
                        )
                    }
                } else {
                    setBasketError(errorMessage)
                }
            }
        } catch (e: Exception) {
            setBasketError(e.message ?: "Ein Fehler ist aufgetreten")
        }
    }
}

/**
 * Put a restored draft's slot back on the basket screen.
 *
 * The basket repository carries the items; the chosen market and date live in
 * screen state, so without this a restored draft comes back with its slot blank
 * and the buyer has to pick it again.
 */
internal fun BuyAppViewModel.restoreDraftSlotSelection(draft: DraftBasket) {
    val key = draft.selectedPickupDate ?: return
    val date = parsePickupDateFromKey(key) ?: return
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                selectedPickupDate = date,
                selectedMarketId = draft.selectedMarketId
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenLoadAvailableDates() {
    bLog("📅 BuyAppViewModel.basketScreenLoadAvailableDates: START")
    val state = _state.value
    // Only the markets this buyer may collect at: a stall they are not scoped to
    // is not theirs to order at, so it must not appear among the slots.
    val markets = state.eligibleMarkets

    // Each market offers its own day. With none configured the app falls back to
    // the single scheduled pickup day, which is every seller today.
    // The next open slot of each market and no further: an order snapshots the price
    // and the catalogue it was placed against, and neither can be promised weeks out.
    // `doc/guides/ordering-business-rules.md` §2.
    val slots = PickupSlots.nextPerMarket(
        markets = markets,
        closures = state.sellerClosures
    ).ifEmpty {
        OrderDateUtils.getAvailablePickupDates(count = 1, closures = state.sellerClosures)
            .map { PickupSlot(marketId = "", date = it.toEpochMilliseconds()) }
    }

    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                availableSlots = slots,
                // Kept in step for everything that still reads dates alone.
                availablePickupDates = slots.map { it.date }.distinct(),
                marketNames = markets.associate { it.id to it.name }
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenShowDatePicker() {
    // Always recompute, never just when the list is empty.
    //
    // The dates are a pure function of the clock, the schedule and the seller's
    // closures, and all three move after init() builds the first list: closures
    // arrive from Firebase moments later, and an app left open crosses the Tuesday
    // deadline and midnight. Caching the list and only refilling it when empty meant
    // a day the seller had closed stayed on offer for the rest of the session.
    basketScreenLoadAvailableDates()
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                showDatePicker = true,
                // Reached from the checkout confirmation when the buyer changes where
                // or when they collect. Closed here rather than stacked behind the
                // picker: a price-change warning may follow the new slot, and it must
                // not open underneath a confirmation showing the old one.
                showCheckoutConfirm = false
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenHideDatePicker() {
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(showDatePicker = false)
        )
    }
}

internal fun BuyAppViewModel.basketScreenSelectPickupDate(date: Long) {
    val state = _state.value
    // The slot this date belongs to, so the market is chosen with it rather than
    // after it. With one market there is exactly one slot per date.
    val slot = state.basketScreen.availableSlots.firstOrNull { it.date == date }
    val isValid = if (slot != null && slot.marketId.isNotEmpty()) {
        PickupSlots.isValid(slot, state.eligibleMarkets, state.sellerClosures)
    } else {
        OrderDateUtils.isPickupDateValid(
            Instant.fromEpochMilliseconds(date),
            closures = state.sellerClosures
        )
    }
    if (!isValid) {
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(
                    orderError = "Gewähltes Datum ist nicht mehr verfügbar.",
                    selectedPickupDate = null,
                    showDatePicker = true
                )
            )
        }
        basketScreenLoadAvailableDates()
        return
    }

    // Switching to a market that prices this basket differently is shown and asked
    // about, never applied quietly: the buyer has already read a total.
    val changes = slotPriceChanges(state, slot)
    if (changes.isNotEmpty()) {
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(
                    showDatePicker = false,
                    orderError = null,
                    pendingSlotPriceChange = PendingSlotPriceChange(
                        slot ?: PickupSlot("", date),
                        changes
                    )
                )
            )
        }
        return
    }

    basketScreenApplySlot(date, slot?.marketId.orEmpty())
}

/**
 * Settle on a pickup slot and make the basket's prices match the market it names.
 *
 * The two have to move together. Leaving the prices behind is what made the switch
 * warning a lie: it told the buyer four articles would change price, and then the
 * total stayed exactly as it was because every item still carried the price it was
 * added at. Repricing is a no-op when the market has no conditions, which is every
 * single-market seller today, so the common case pays nothing for this.
 *
 * Only the prices move. Quantities are the buyer's, and a market change has no
 * business touching them — see `BasketRepository.repriceItems`.
 */
private fun BuyAppViewModel.basketScreenApplySlot(date: Long, marketId: String) {
    val before = _state.value.basketScreen
    val marketChanged = marketId != before.selectedMarketId

    // The slot decides whether delivery is on offer at all, so it is settled here and
    // not at the checkout chips: a buyer can reach the write by editing a placed order
    // without the dialog ever opening. A stall that does not deliver turns the order
    // into a pickup, and the buyer is told rather than finding out from the order.
    val market = _state.value.eligibleMarkets.firstOrNull { it.id == marketId }
    val fulfilment = Fulfilment.forMarket(market, before.fulfilment)
    val fulfilmentCoerced = fulfilment != before.fulfilment

    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                selectedPickupDate = date,
                selectedMarketId = marketId,
                fulfilment = fulfilment,
                showDatePicker = false,
                orderError = null,
                pendingSlotPriceChange = null
            )
        )
    }

    if (fulfilmentCoerced) {
        bLog("🛒 basketScreenApplySlot: $marketId is pickup-only, order is now a pickup")
        viewModelScope.launch {
            showSnackBar(
                "${market?.name.orEmpty().ifBlank { "Dieser Markt" }} liefert nicht — " +
                    "die Bestellung ist eine Abholung.",
                SnackbarType.WARNING
            )
        }
    }

    if (marketChanged) {
        viewModelScope.launch {
            basketRepository.repriceItems(_state.value.basketPricesAt(marketId))
        }
    }
}

/**
 * What moving the basket to [slot]'s market would do to its prices.
 *
 * Empty when nothing moves — same market, no conditions either side, or an empty
 * basket — which is every seller today and stays silent.
 */
private fun slotPriceChanges(state: BuyAppState, slot: PickupSlot?): List<PriceChange> {
    val toMarketId = slot?.marketId.orEmpty()
    val fromMarketId = state.basketScreen.selectedMarketId
    if (toMarketId == fromMarketId) return emptyList()

    // The buyer's *first* pick is not a change. With no market settled the basket was
    // priced against no conditions, so moving to a real market does shift the numbers
    // — but there is no total the buyer committed to, and a dialog saying "4 articles
    // change price" before they have chosen anything reads as a warning about
    // something they did not do. The prices still move; see basketScreenApplySlot.
    if (fromMarketId.isEmpty()) return emptyList()

    val pricingFor = { id: String ->
        state.eligibleMarkets.firstOrNull { it.id == id }?.pricing ?: MarketPricing.NONE
    }
    // Priced against the catalogue entries the basket was built from.
    val articles = state.basketScreen.items.mapNotNull { item ->
        state.mainScreen.articles.firstOrNull { it.id == item.productId }
    }
    if (articles.isEmpty()) return emptyList()

    return priceChanges(articles, pricingFor(fromMarketId), pricingFor(toMarketId))
}

/** The buyer accepted the new prices: apply the slot they had chosen, prices and all. */
internal fun BuyAppViewModel.basketScreenConfirmSlotPriceChange() {
    val pending = _state.value.basketScreen.pendingSlotPriceChange ?: return
    basketScreenApplySlot(pending.slot.date, pending.slot.marketId)
}

/** The buyer declined: the slot they had is kept, prices unchanged. */
internal fun BuyAppViewModel.basketScreenDismissSlotPriceChange() {
    _state.update { current ->
        current.copy(basketScreen = current.basketScreen.copy(pendingSlotPriceChange = null))
    }
}

internal fun BuyAppViewModel.basketScreenShowReorderDatePicker() {
    if (_state.value.basketScreen.availablePickupDates.isEmpty()) {
        basketScreenLoadAvailableDates()
    }
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(showReorderDatePicker = true)
        )
    }
}

internal fun BuyAppViewModel.basketScreenHideReorderDatePicker() {
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(showReorderDatePicker = false)
        )
    }
}

internal fun BuyAppViewModel.basketScreenReorderWithNewDate(newPickupDate: Long, currentArticles: List<Article>) {
    viewModelScope.launch {
        bLog("🛒 BuyAppViewModel.basketScreenReorderWithNewDate: START")
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(
                    isReordering = true,
                    showReorderDatePicker = false,
                    orderError = null,
                    reorderSuccess = false
                )
            )
        }

        try {
            val isDateValid = OrderDateUtils.isPickupDateValid(
                Instant.fromEpochMilliseconds(newPickupDate),
                closures = _state.value.sellerClosures
            )
            if (!isDateValid) {
                _state.update { current ->
                    current.copy(
                        basketScreen = current.basketScreen.copy(
                            isReordering = false,
                            orderError = "Gewähltes Datum ist nicht mehr verfügbar.",
                            showReorderDatePicker = true
                        )
                    )
                }
                basketScreenLoadAvailableDates()
                return@launch
            }

            val currentItems = _state.value.basketScreen.items
            if (currentItems.isEmpty()) {
                setBasketError("Keine Artikel zum Nachbestellen")
                return@launch
            }

            // As above: an article the seller has withdrawn does not come along.
            val dropped = currentItems.unorderableAgainst(currentArticles)
            val updatedItems = mutableListOf<OrderedProduct>()
            for (item in currentItems.orderableOnly(currentArticles)) {
                val article = currentArticles.find { it.id == item.articleKey() }
                if (article != null) {
                    updatedItems.add(item.copy(
                        price = article.price,
                        productName = article.productName,
                        unit = article.unit
                    ))
                } else {
                    updatedItems.add(item)
                }
            }
            if (updatedItems.isEmpty()) {
                setBasketError(
                    if (dropped.isEmpty()) "Keine Artikel zum Nachbestellen"
                    else droppedFromReorderMessage(dropped)
                )
                return@launch
            }

            basketRepository.clearBasket()
            for (item in updatedItems) {
                basketRepository.addItem(item)
            }

            val newTotal = updatedItems.sumOf { it.price * it.amountCount }

            _state.update { current ->
                current.copy(
                    basketScreen = current.basketScreen.copy(
                        items = updatedItems,
                        total = newTotal,
                        orderId = null,
                        orderDate = null,
                        pickupDate = null,
                        createdDate = null,
                        isEditMode = false,
                        canEdit = true,
                        originalOrderItems = emptyList(),
                        hasChanges = false,
                        selectedPickupDate = newPickupDate,
                        isReordering = false,
                        reorderSuccess = true
                    )
                )
            }
        } catch (e: Exception) {
            setBasketError(e.message ?: "Ein Fehler ist aufgetreten")
        }
    }
}

internal fun BuyAppViewModel.basketScreenCalculateMergeConflicts(
    newItems: List<OrderedProduct>,
    existingItems: List<OrderedProduct>
): List<MergeConflict> {
    val conflicts = mutableListOf<MergeConflict>()

    // 1. Check items in NEW basket
    for (newItem in newItems) {
        val existingItem = existingItems.find { it.productId == newItem.productId }
        if (existingItem == null) {
            // Item added (not in existing order)
            conflicts.add(MergeConflict(
                productId = newItem.productId,
                productName = newItem.productName,
                unit = newItem.unit,
                conflictType = MergeConflictType.ITEM_ADDED,
                existingQuantity = 0.0,
                newQuantity = newItem.amountCount,
                existingPrice = 0.0,
                newPrice = newItem.price,
                resolution = MergeResolution.USE_NEW // Default: add the new item
            ))
        } else if (existingItem.amountCount != newItem.amountCount) {
            // Quantity changed
            conflicts.add(MergeConflict(
                productId = newItem.productId,
                productName = newItem.productName,
                unit = newItem.unit,
                conflictType = MergeConflictType.QUANTITY_CHANGED,
                existingQuantity = existingItem.amountCount,
                newQuantity = newItem.amountCount,
                existingPrice = existingItem.price,
                newPrice = newItem.price,
                resolution = MergeResolution.UNDECIDED
            ))
        }
    }

    // 2. Check items REMOVED (in existing but not in new)
    for (existingItem in existingItems) {
        val newItem = newItems.find { it.productId == existingItem.productId }
        if (newItem == null) {
            conflicts.add(MergeConflict(
                productId = existingItem.productId,
                productName = existingItem.productName,
                unit = existingItem.unit,
                conflictType = MergeConflictType.ITEM_REMOVED,
                existingQuantity = existingItem.amountCount,
                newQuantity = 0.0,
                existingPrice = existingItem.price,
                newPrice = 0.0,
                resolution = MergeResolution.KEEP_EXISTING // Default: keep the existing item
            ))
        }
    }

    return conflicts
}

internal fun BuyAppViewModel.basketScreenHideMergeDialog() {
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                showMergeDialog = false,
                existingOrderForMerge = null,
                mergeConflicts = emptyList()
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenResolveMergeConflict(productId: String, resolution: MergeResolution) {
    val updatedConflicts = _state.value.basketScreen.mergeConflicts.map { conflict ->
        if (conflict.productId == productId) conflict.copy(resolution = resolution) else conflict
    }
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(mergeConflicts = updatedConflicts)
        )
    }
}

internal fun BuyAppViewModel.basketScreenConfirmMerge() {
    if (_state.value.basketScreen.mergeMode == BasketMergeMode.HISTORY_REORDER) {
        confirmHistoryMergeIntoDraft()
        return
    }
    viewModelScope.launch {
        bLog("🔀 BuyAppViewModel.basketScreenConfirmMerge: START")
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(isMerging = true)
            )
        }

        try {
            val basketState = _state.value.basketScreen
            val existingOrder = basketState.existingOrderForMerge

            if (existingOrder == null) {
                setBasketError("Keine bestehende Bestellung zum Zusammenführen")
                return@launch
            }

            val conflicts = basketState.mergeConflicts
            val newItems = basketState.items

            val mergedItems = mutableListOf<OrderedProduct>()
            val processedProductIds = mutableSetOf<String>()

            for (existingItem in existingOrder.articles) {
                val conflict = conflicts.find { it.productId == existingItem.productId }
                val newItem = newItems.find { it.productId == existingItem.productId }

                val finalItem = when {
                    conflict != null -> when (conflict.resolution) {
                        MergeResolution.ADD -> existingItem.copy(
                            amountCount = existingItem.amountCount + (newItem?.amountCount ?: 0.0),
                            price = newItem?.price ?: existingItem.price
                        )
                        MergeResolution.KEEP_EXISTING -> existingItem
                        MergeResolution.USE_NEW -> newItem ?: existingItem
                        MergeResolution.UNDECIDED -> existingItem
                    }
                    newItem != null -> newItem
                    else -> existingItem
                }
                mergedItems.add(finalItem)
                processedProductIds.add(existingItem.productId)
            }

            for (newItem in newItems) {
                if (newItem.productId !in processedProductIds) {
                    mergedItems.add(newItem)
                }
            }

            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                setBasketError("Benutzer nicht angemeldet")
                return@launch
            }

            val buyerProfile = getBuyerProfileOrFallback(currentUserId)

            val mergedOrder = existingOrder.copy(
                buyerProfile = buyerProfile,
                articles = mergedItems
            )

            val result = orderRepository.updateOrder(mergedOrder)
            result.onSuccess {
                val dateKey = basketScreenFormatDateKey(existingOrder.pickUpDate)
                basketRepository.loadOrderItems(mergedItems, existingOrder.id, dateKey)

                // Clear draft basket from profile since order is now placed/merged
                try {
                    profileRepository.clearDraftBasket()
                    bLog("🔀 basketScreenConfirmMerge: Cleared draft basket after merge")
                } catch (e: Exception) {
                    bLog("⚠️ basketScreenConfirmMerge: Failed to clear draft basket: ${e.message}")
                }

                _state.update { current ->
                    current.copy(
                        basketScreen = current.basketScreen.copy(
                            showMergeDialog = false,
                            existingOrderForMerge = null,
                            mergeConflicts = emptyList(),
                            isMerging = false,
                            orderSuccess = true,
                            orderId = existingOrder.id,
                            orderDate = dateKey,
                            pickupDate = existingOrder.pickUpDate,
                            createdDate = existingOrder.createdDate,
                            items = mergedItems,
                            total = mergedItems.sumOf { it.price * it.amountCount },
                            originalOrderItems = mergedItems,
                            hasChanges = false
                        )
                    )
                }
            }.onFailure { error ->
                setBasketError("Zusammenführung fehlgeschlagen: ${error.message}")
            }
        } catch (e: Exception) {
            setBasketError("Ein Fehler ist aufgetreten: ${e.message}")
        }
    }
}

/**
 * Atomic migration: when the 3rd demo order is placed, fetch the 2 prior Firebase demo orders,
 * save all 3 locally, mark local mode, then delete the Firebase copies.
 *
 * Failure before [sellerConfig.markDemoLocalMode] leaves no side effects — caller surfaces error
 * and user can retry. Post-marking, Firebase delete is best-effort (local data is safe).
 */
internal suspend fun BuyAppViewModel.performDemoMigration(newOrder: Order): Result<Order> {
    return try {
        Log.d(TAG) { "performDemoMigration: START" }
        val buyerProfile = profileRepository.getBuyerProfile().getOrThrow()

        // Fetch the two existing demo orders from Firebase
        val existingOrdersResult = orderRepository.getBuyerOrders(
            sellerId = sellerConfig.sellerId,
            placedOrderIds = buyerProfile.placedOrderIds,
            isDemo = true
        )

        existingOrdersResult.onSuccess { existingOrders ->
            // Save existing orders locally
            existingOrders.forEach { sellerConfig.saveDemoOrder(it) }
            Log.d(TAG) { "performDemoMigration: Saved ${existingOrders.size} existing demo orders locally" }
        }.onFailure {
            Log.e(TAG) { "performDemoMigration: Failed to fetch existing demo orders — ${it.message}" }
            return Result.failure(it)
        }

        // Assign a stable local ID to the new order before any side effects
        val localOrder = newOrder.copy(id = "demo_${Clock.System.now().toEpochMilliseconds()}")

        // Save new order locally
        sellerConfig.saveDemoOrder(localOrder)

        // Mark fully-local mode — subsequent orders skip Firebase entirely
        sellerConfig.markDemoLocalMode()
        Log.d(TAG) { "performDemoMigration: Saved order 3 locally, marked local mode" }

        // Delete the 2 prior Firebase copies (best-effort — local data is already safe)
        if (buyerProfile.placedOrderIds.isNotEmpty()) {
            orderRepository.deleteDemoOrders(sellerConfig.sellerId, buyerProfile.placedOrderIds)
                .onFailure { e ->
                    Log.w(TAG) { "performDemoMigration: Firebase delete failed (non-fatal): ${e.message}" }
                }
            // Remove Firebase-tracked IDs from buyer profile so the observer doesn't refetch them
            profileRepository.saveBuyerProfile(buyerProfile.copy(placedOrderIds = emptyMap()))
        }

        loadOrderHistory()
        Log.d(TAG) { "performDemoMigration: Complete — 2 prior orders local, 1 new local order placed" }
        Result.success(localOrder)
    } catch (e: Exception) {
        Log.e(TAG) { "performDemoMigration: Failed — ${e.message}" }
        Result.failure(e)
    }
}

internal fun basketScreenFormatDateKey(timestamp: Long): String {
    val dateTime = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val month = dateTime.month.number.toString().padStart(2, '0')
    val day = dateTime.day.toString().padStart(2, '0')
    return "${dateTime.year}$month$day"
}

// ===== Draft Warning Dialog Functions =====

/**
 * Hide the draft warning dialog without taking any action
 */
internal fun BuyAppViewModel.basketScreenHideDraftWarningDialog() {
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                showDraftWarningDialog = false,
                draftItemCount = 0,
                pendingOrderIdForLoad = null,
                pendingOrderDateForLoad = null
            )
        )
    }
}

/**
 * Save the current draft to profile and then load the pending order
 */
internal fun BuyAppViewModel.basketScreenSaveDraftAndLoadOrder() {
    viewModelScope.launch {
        val pendingOrderId = _state.value.basketScreen.pendingOrderIdForLoad
        val pendingOrderDate = _state.value.basketScreen.pendingOrderDateForLoad

        if (pendingOrderId == null || pendingOrderDate == null) {
            bLog("⚠️ basketScreenSaveDraftAndLoadOrder: No pending order info")
            basketScreenHideDraftWarningDialog()
            return@launch
        }

        // Save current draft to profile
        try {
            saveDraftBasketToProfile()
            bLog("✅ basketScreenSaveDraftAndLoadOrder: Draft saved successfully")
        } catch (e: Exception) {
            bLog("⚠️ basketScreenSaveDraftAndLoadOrder: Failed to save draft: ${e.message}")
        }

        // Hide dialog and load the order
        basketScreenHideDraftWarningDialog()
        basketScreenLoadOrder(pendingOrderId, pendingOrderDate, forceLoad = true)
    }
}

/**
 * Discard the current draft and load the pending order
 */
internal fun BuyAppViewModel.basketScreenDiscardDraftAndLoadOrder() {
    viewModelScope.launch {
        val pendingOrderId = _state.value.basketScreen.pendingOrderIdForLoad
        val pendingOrderDate = _state.value.basketScreen.pendingOrderDateForLoad

        if (pendingOrderId == null || pendingOrderDate == null) {
            bLog("⚠️ basketScreenDiscardDraftAndLoadOrder: No pending order info")
            basketScreenHideDraftWarningDialog()
            return@launch
        }

        // Clear the draft from profile
        try {
            profileRepository.clearDraftBasket()
            bLog("✅ basketScreenDiscardDraftAndLoadOrder: Draft cleared")
        } catch (e: Exception) {
            bLog("⚠️ basketScreenDiscardDraftAndLoadOrder: Failed to clear draft: ${e.message}")
        }

        // Hide dialog and load the order
        basketScreenHideDraftWarningDialog()
        basketScreenLoadOrder(pendingOrderId, pendingOrderDate, forceLoad = true)
    }
}

/**
 * Show draft warning dialog before loading an order
 * Returns true if dialog was shown, false if no draft exists
 */
internal fun BuyAppViewModel.basketScreenShowDraftWarningIfNeeded(orderId: String, date: String): Boolean {
    val hasDraft = basketRepository.hasDraftBasket()
    val draftItemCount = basketRepository.observeBasket().value.size

    if (hasDraft && draftItemCount > 0) {
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(
                    showDraftWarningDialog = true,
                    draftItemCount = draftItemCount,
                    pendingOrderIdForLoad = orderId,
                    pendingOrderDateForLoad = date
                )
            )
        }
        return true
    }
    return false
}

internal fun BuyAppViewModel.confirmHistoryMergeIntoDraft() {
    viewModelScope.launch {
        bLog("🔀 confirmHistoryMergeIntoDraft: START")
        _state.update { current ->
            current.copy(basketScreen = current.basketScreen.copy(isMerging = true))
        }
        try {
            val basketState = _state.value.basketScreen
            val historicOrder = basketState.existingOrderForMerge
            if (historicOrder == null) {
                setBasketError("Keine bestehende Bestellung zum Zusammenführen")
                return@launch
            }
            val historicItems = historicOrder.articles
            val draftItems = basketState.items
            val currentArticles = _state.value.mainScreen.articles

            val mergedRaw = mergeItemsWithResolutions(
                historicItems = historicItems,
                draftItems = draftItems,
                conflicts = basketState.mergeConflicts
            )
            val merged = mergedRaw.map { correctArticleData(it, currentArticles) }

            basketRepository.clearBasket()
            for (item in merged) basketRepository.addItem(item)

            // Preserve any in-progress placed order so the basket screen continues to show
            // "Update Order" rather than reverting to the checkout/order CTA.
            val hasPlacedOrder = basketState.orderId != null
            val hasChanges = if (hasPlacedOrder)
                basketScreenCheckIfHasChanges(merged, basketState.originalOrderItems) else false

            _state.update { current ->
                current.copy(
                    basketScreen = current.basketScreen.copy(
                        items = merged,
                        total = merged.sumOf { it.price * it.amountCount },
                        showMergeDialog = false,
                        existingOrderForMerge = null,
                        mergeConflicts = emptyList(),
                        isMerging = false,
                        mergeMode = BasketMergeMode.CHECKOUT_EXISTING_ORDER,
                        hasChanges = hasChanges
                    )
                )
            }
            bLog("🔀 confirmHistoryMergeIntoDraft: ${merged.size} items written; placedOrder=$hasPlacedOrder")
        } catch (e: Exception) {
            setBasketError("Zusammenführung fehlgeschlagen: ${e.message}")
            _state.update { current ->
                current.copy(basketScreen = current.basketScreen.copy(isMerging = false))
            }
        }
    }
}
