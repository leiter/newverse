package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.state.BasketScreenState
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.MergeConflict
import com.together.newverse.ui.state.MergeConflictType
import com.together.newverse.ui.state.MergeResolution
import com.together.newverse.ui.state.BuyBasketScreenAction
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

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
    if (LOG_BASKET) println(message)
}

/**
 * Reset all in-flight basket flags and set/clear the basket-screen error message.
 * Used as the common exit point for checkout / update / cancel / merge / reorder failures.
 */
private fun BuyAppViewModel.setBasketError(message: String?) {
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                isCheckingOut = false,
                isCancelling = false,
                isMerging = false,
                isReordering = false,
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
        BuyBasketScreenAction.Checkout -> basketScreenCheckout()
        is BuyBasketScreenAction.LoadOrder -> basketScreenLoadOrder(action.orderId, action.date, forceLoad = true)
        BuyBasketScreenAction.UpdateOrder -> basketScreenUpdateOrder()
        BuyBasketScreenAction.EnableEditing -> basketScreenEnableEditing()
        BuyBasketScreenAction.ResetOrderState -> basketScreenResetOrderState()
        BuyBasketScreenAction.ShowDatePicker -> basketScreenShowDatePicker()
        BuyBasketScreenAction.HideDatePicker -> basketScreenHideDatePicker()
        is BuyBasketScreenAction.SelectPickupDate -> basketScreenSelectPickupDate(action.date)
        BuyBasketScreenAction.LoadAvailableDates -> basketScreenLoadAvailableDates()
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
                        hasChanges = hasChanges
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
    val draftBasket = basketRepository.toDraftBasket(selectedDateKey)

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
                    if (loadedOrder.status == com.together.newverse.domain.model.OrderStatus.CANCELLED ||
                        loadedOrder.status == com.together.newverse.domain.model.OrderStatus.COMPLETED) {
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

        // If the last item of a placed, editable order is removed, cancel it and reset to a fresh draft.
        if (isPlacedOrder && isNowEmpty) {
            bLog("🛒 basketScreenRemoveItem: Last item removed from placed order, resetting to empty draft.")
            basketScreenResetToEmptyDraft()
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

internal fun BuyAppViewModel.basketScreenClearBasket() {
    viewModelScope.launch {
        basketRepository.clearBasket()
    }
}

internal fun BuyAppViewModel.basketScreenCheckout() {
    viewModelScope.launch {
        bLog("🛒 BuyAppViewModel.basketScreenCheckout: START")
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(
                    isCheckingOut = true,
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
            val isBlocked = profileRepository.isClientBlocked(sellerConfig.sellerId, currentUserId)
            if (isBlocked) {
                setBasketError("You have been blocked by this seller")
                return@launch
            }

            val items = _state.value.basketScreen.items
            if (items.isEmpty()) {
                setBasketError("Warenkorb ist leer")
                return@launch
            }

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

            val isDateValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(selectedDate))
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

            val order = Order(
                buyerProfile = buyerProfile,
                createdDate = Clock.System.now().toEpochMilliseconds(),
                sellerId = sellerConfig.sellerId,
                marketId = "",
                pickUpDate = selectedDate,
                message = "",
                articles = items,
                isDemoOrder = _state.value.isDemoMode,
                status = if (_state.value.isDemoMode) com.together.newverse.domain.model.OrderStatus.DEMO_ORDER else com.together.newverse.domain.model.OrderStatus.PLACED
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
    val canEdit = _state.value.basketScreen.canEdit
    if (canEdit) {
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(isEditMode = true)
            )
        }
    } else {
        _state.update { current ->
            current.copy(
                basketScreen = current.basketScreen.copy(
                    orderError = "Bestellung kann nicht mehr bearbeitet werden (Frist: Dienstag 23:59)"
                )
            )
        }
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
                setBasketError("Bearbeitungsfrist abgelaufen (Dienstag 23:59)")
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

            val buyerProfile = getBuyerProfileOrFallback(currentUserId)
            val isDemo = _state.value.isDemoMode

            val updatedOrder = Order(
                id = orderId,
                buyerProfile = buyerProfile,
                createdDate = createdDate,
                sellerId = sellerConfig.sellerId,
                marketId = "",
                pickUpDate = pickupDate,
                message = "",
                articles = items,
                isDemoOrder = isDemo
            )

            val result = if (isDemo) {
                if (orderId.startsWith("demo_")) {
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
                    cancelSuccess = false
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
                return@launch
            }

            val canEdit = OrderDateUtils.canEditOrder(Instant.fromEpochMilliseconds(pickupDate))
            if (!canEdit) {
                setBasketError("Stornierung nicht mehr möglich (Frist: Dienstag 23:59)")
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
                            availablePickupDates = availableDates
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
                                availablePickupDates = availableDates
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

internal fun BuyAppViewModel.basketScreenLoadAvailableDates() {
    bLog("📅 BuyAppViewModel.basketScreenLoadAvailableDates: START")
    val dates = OrderDateUtils.getAvailablePickupDates(count = 5)
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                availablePickupDates = dates.map { it.toEpochMilliseconds() }
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenShowDatePicker() {
    if (_state.value.basketScreen.availablePickupDates.isEmpty()) {
        basketScreenLoadAvailableDates()
    }
    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(showDatePicker = true)
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
    val isValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(date))
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

    _state.update { current ->
        current.copy(
            basketScreen = current.basketScreen.copy(
                selectedPickupDate = date,
                showDatePicker = false,
                orderError = null
            )
        )
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
            val isDateValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(newPickupDate))
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

            val updatedItems = mutableListOf<OrderedProduct>()
            for (item in currentItems) {
                val article = currentArticles.find { it.id == item.productId }
                if (article != null && article.available) {
                    updatedItems.add(item.copy(
                        price = article.price,
                        productName = article.productName,
                        unit = article.unit
                    ))
                } else {
                    updatedItems.add(item)
                }
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
        println("🛒 performDemoMigration: START")
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
            println("🛒 performDemoMigration: Saved ${existingOrders.size} existing demo orders locally")
        }.onFailure {
            println("❌ performDemoMigration: Failed to fetch existing demo orders — ${it.message}")
            return Result.failure(it)
        }

        // Assign a stable local ID to the new order before any side effects
        val localOrder = newOrder.copy(id = "demo_${Clock.System.now().toEpochMilliseconds()}")

        // Save new order locally
        sellerConfig.saveDemoOrder(localOrder)

        // Mark fully-local mode — subsequent orders skip Firebase entirely
        sellerConfig.markDemoLocalMode()
        println("🛒 performDemoMigration: Saved order 3 locally, marked local mode")

        // Delete the 2 prior Firebase copies (best-effort — local data is already safe)
        if (buyerProfile.placedOrderIds.isNotEmpty()) {
            orderRepository.deleteDemoOrders(sellerConfig.sellerId, buyerProfile.placedOrderIds)
                .onFailure { e ->
                    println("⚠️ performDemoMigration: Firebase delete failed (non-fatal): ${e.message}")
                }
            // Remove Firebase-tracked IDs from buyer profile so the observer doesn't refetch them
            profileRepository.saveBuyerProfile(buyerProfile.copy(placedOrderIds = emptyMap()))
        }

        loadOrderHistory()
        println("✅ performDemoMigration: Complete — 2 prior orders local, 1 new local order placed")
        Result.success(localOrder)
    } catch (e: Exception) {
        println("❌ performDemoMigration: Failed — ${e.message}")
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
