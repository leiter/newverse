package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.data.repository.GitLiveArticleRepository
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.state.BasketScreenState
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.MergeConflict
import com.together.newverse.ui.state.MergeConflictType
import com.together.newverse.ui.state.MergeResolution
import com.together.newverse.ui.state.UnifiedBasketScreenAction
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

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
 * - Helpers: basketScreenCheckIfHasChanges, basketScreenFormatDateKey, basketScreenFormatDate, basketScreenCanEditOrder, basketScreenGetDaysUntilPickup
 */

// Seller ID constant
private const val BASKET_SELLER_ID = GitLiveArticleRepository.DEFAULT_SELLER_ID

// Debounce delay for saving draft basket (ms)
private const val DRAFT_SAVE_DEBOUNCE_MS = 2000L

// Job for debounced draft basket saving
private var draftSaveJob: Job? = null

internal fun BuyAppViewModel.handleBasketScreenAction(action: UnifiedBasketScreenAction) {
    when (action) {
        is UnifiedBasketScreenAction.AddItem -> basketScreenAddItem(action.item)
        is UnifiedBasketScreenAction.RemoveItem -> basketScreenRemoveItem(action.productId)
        is UnifiedBasketScreenAction.UpdateItemQuantity -> basketScreenUpdateQuantity(action.productId, action.newQuantity)
        UnifiedBasketScreenAction.ClearBasket -> basketScreenClearBasket()
        UnifiedBasketScreenAction.Checkout -> basketScreenCheckout()
        is UnifiedBasketScreenAction.LoadOrder -> basketScreenLoadOrder(action.orderId, action.date, forceLoad = true)
        UnifiedBasketScreenAction.UpdateOrder -> basketScreenUpdateOrder()
        UnifiedBasketScreenAction.EnableEditing -> basketScreenEnableEditing()
        UnifiedBasketScreenAction.ResetOrderState -> basketScreenResetOrderState()
        UnifiedBasketScreenAction.ShowDatePicker -> basketScreenShowDatePicker()
        UnifiedBasketScreenAction.HideDatePicker -> basketScreenHideDatePicker()
        is UnifiedBasketScreenAction.SelectPickupDate -> basketScreenSelectPickupDate(action.date)
        UnifiedBasketScreenAction.LoadAvailableDates -> basketScreenLoadAvailableDates()
        UnifiedBasketScreenAction.CancelOrder -> basketScreenCancelOrder()
        UnifiedBasketScreenAction.ShowReorderDatePicker -> basketScreenShowReorderDatePicker()
        UnifiedBasketScreenAction.HideReorderDatePicker -> basketScreenHideReorderDatePicker()
        is UnifiedBasketScreenAction.ReorderWithNewDate -> basketScreenReorderWithNewDate(action.newPickupDate, action.currentArticles)
        UnifiedBasketScreenAction.HideMergeDialog -> basketScreenHideMergeDialog()
        is UnifiedBasketScreenAction.ResolveMergeConflict -> basketScreenResolveMergeConflict(action.productId, action.resolution)
        UnifiedBasketScreenAction.ConfirmMerge -> basketScreenConfirmMerge()
        UnifiedBasketScreenAction.HideDraftWarningDialog -> basketScreenHideDraftWarningDialog()
        UnifiedBasketScreenAction.SaveDraftAndLoadOrder -> basketScreenSaveDraftAndLoadOrder()
        UnifiedBasketScreenAction.DiscardDraftAndLoadOrder -> basketScreenDiscardDraftAndLoadOrder()
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
            val hasChanges = basketScreenCheckIfHasChanges(items, _state.value.screens.basketScreen.originalOrderItems)
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            items = items,
                            total = basketRepository.getTotal(),
                            hasChanges = hasChanges
                        )
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
        println("🛒 saveDraftBasketToProfile: Skipping - not a draft basket")
        return
    }

    val selectedDateTimestamp = _state.value.screens.basketScreen.selectedPickupDate
    val selectedDateKey = selectedDateTimestamp?.let { basketScreenFormatDateKey(it) }
    val draftBasket = basketRepository.toDraftBasket(selectedDateKey)

    if (draftBasket.items.isEmpty()) {
        // Clear draft basket if empty
        println("🛒 saveDraftBasketToProfile: Basket empty, clearing draft")
        profileRepository.clearDraftBasket()
        return
    }

    println("🛒 saveDraftBasketToProfile: Saving ${draftBasket.items.size} items to profile")
    profileRepository.saveDraftBasket(draftBasket).fold(
        onSuccess = {
            println("✅ saveDraftBasketToProfile: Draft basket saved")
        },
        onFailure = { error ->
            println("❌ saveDraftBasketToProfile: Failed - ${error.message}")
        }
    )
}

internal fun BuyAppViewModel.basketScreenCheckIfHasChanges(currentItems: List<OrderedProduct>, originalItems: List<OrderedProduct>): Boolean {
    if (originalItems.isEmpty() && currentItems.isEmpty()) return false
    if (originalItems.isEmpty()) return true
    if (currentItems.size != originalItems.size) return true

    currentItems.forEach { currentItem ->
        val originalItem = originalItems.find { it.productId == currentItem.productId }
        if (originalItem == null) return true
        if (originalItem.amountCount != currentItem.amountCount) return true
    }

    originalItems.forEach { originalItem ->
        val currentItem = currentItems.find { it.productId == originalItem.productId }
        if (currentItem == null) return true
    }

    return false
}

internal fun BuyAppViewModel.basketScreenLoadMostRecentEditableOrder() {
    viewModelScope.launch {
        try {
            println("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: START")

            val loadedOrderInfo = basketRepository.getLoadedOrderInfo()
            if (loadedOrderInfo != null) {
                val (orderId, orderDate) = loadedOrderInfo
                println("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Order already loaded - orderId=$orderId, date=$orderDate")

                val orderPath = "orders/$BASKET_SELLER_ID/$orderDate/$orderId"
                val result = orderRepository.loadOrder(BASKET_SELLER_ID, orderId, orderPath)
                result.onSuccess { order ->
                    // Check if order is still editable (not cancelled/completed)
                    if (order.status == com.together.newverse.domain.model.OrderStatus.CANCELLED ||
                        order.status == com.together.newverse.domain.model.OrderStatus.COMPLETED) {
                        println("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Order is finalized, clearing basket")
                        basketRepository.clearBasket()
                        return@onSuccess
                    }

                    val canEdit = OrderDateUtils.canEditOrder(
                        Instant.fromEpochMilliseconds(order.pickUpDate)
                    )
                    val currentBasketItems = basketRepository.observeBasket().value
                    val hasChanges = basketScreenCheckIfHasChanges(currentBasketItems, order.articles)

                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
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
                        )
                    }
                }.onFailure { error ->
                    println("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Failed to load order - ${error.message}")
                    // Clear loaded order info since loading failed
                    basketRepository.clearBasket()
                }
                return@launch
            }

            val profileResult = profileRepository.getBuyerProfile()
            val buyerProfile = profileResult.getOrNull()

            if (buyerProfile == null || buyerProfile.placedOrderIds.isEmpty()) {
                println("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: No buyer profile or orders")
                return@launch
            }

            val orderResult = orderRepository.getOpenEditableOrder(BASKET_SELLER_ID, buyerProfile.placedOrderIds)
            val order = orderResult.getOrNull()

            if (order != null) {
                val dateKey = basketScreenFormatDateKey(order.pickUpDate)
                basketScreenLoadOrder(order.id, dateKey)
            }
        } catch (e: Exception) {
            println("❌ BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Error - ${e.message}")
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
    }
}

internal fun BuyAppViewModel.basketScreenUpdateQuantity(productId: String, newQuantity: Double) {
    viewModelScope.launch {
        basketRepository.updateQuantity(productId, newQuantity)
    }
}

internal fun BuyAppViewModel.basketScreenClearBasket() {
    viewModelScope.launch {
        basketRepository.clearBasket()
    }
}

internal fun BuyAppViewModel.basketScreenCheckout() {
    viewModelScope.launch {
        println("🛒 BuyAppViewModel.basketScreenCheckout: START")
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        isCheckingOut = true,
                        orderSuccess = false,
                        orderError = null
                    )
                )
            )
        }

        try {
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = "Bitte melden Sie sich an, um eine Bestellung aufzugeben"
                            )
                        )
                    )
                }
                return@launch
            }

            val items = _state.value.screens.basketScreen.items
            if (items.isEmpty()) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = "Warenkorb ist leer"
                            )
                        )
                    )
                }
                return@launch
            }

            val buyerProfile = try {
                profileRepository.getBuyerProfile().getOrNull() ?: BuyerProfile(
                    id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false
                )
            } catch (e: Exception) {
                BuyerProfile(id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false)
            }

            val selectedDate = _state.value.screens.basketScreen.selectedPickupDate
            if (selectedDate == null) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = "Bitte wählen Sie ein Abholdatum",
                                showDatePicker = true
                            )
                        )
                    )
                }
                return@launch
            }

            val isDateValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(selectedDate))
            if (!isDateValid) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = "Gewähltes Datum ist nicht mehr verfügbar.",
                                selectedPickupDate = null,
                                showDatePicker = true
                            )
                        )
                    )
                }
                basketScreenLoadAvailableDates()
                return@launch
            }

            // DISABLED: Merge order logic - always create new order for now
            // val dateKey = basketScreenFormatDateKey(selectedDate)
            // val existingOrderId = buyerProfile.placedOrderIds[dateKey]
            //
            // if (existingOrderId != null) {
            //     val existingOrderPath = "orders/$BASKET_SELLER_ID/$dateKey/$existingOrderId"
            //     val existingOrderResult = orderRepository.loadOrder(BASKET_SELLER_ID, existingOrderId, existingOrderPath)
            //     existingOrderResult.onSuccess { existingOrder ->
            //         val conflicts = basketScreenCalculateMergeConflicts(items, existingOrder.articles)
            //         _state.update { current ->
            //             current.copy(
            //                 screens = current.screens.copy(
            //                     basketScreen = current.screens.basketScreen.copy(
            //                         isCheckingOut = false,
            //                         showMergeDialog = true,
            //                         existingOrderForMerge = existingOrder,
            //                         mergeConflicts = conflicts
            //                     )
            //                 )
            //             )
            //         }
            //     }.onFailure { error ->
            //         _state.update { current ->
            //             current.copy(
            //                 screens = current.screens.copy(
            //                     basketScreen = current.screens.basketScreen.copy(
            //                         isCheckingOut = false,
            //                         orderError = "Bestellung konnte nicht geladen werden: ${error.message}"
            //                     )
            //                 )
            //             )
            //         }
            //     }
            //     return@launch
            // }

            val order = Order(
                buyerProfile = buyerProfile,
                createdDate = Clock.System.now().toEpochMilliseconds(),
                sellerId = BASKET_SELLER_ID,
                marketId = "",
                pickUpDate = selectedDate,
                message = "",
                articles = items
            )

            val result = orderRepository.placeOrder(order)
            result.onSuccess { placedOrder ->
                // Clear draft basket from profile BEFORE loading the placed order
                // to avoid triggering the draft warning dialog
                try {
                    profileRepository.clearDraftBasket()
                    basketRepository.clearBasket()
                    println("🛒 basketScreenCheckout: Cleared draft basket after order placed")
                } catch (e: Exception) {
                    println("⚠️ basketScreenCheckout: Failed to clear draft basket: ${e.message}")
                }

                val placedDateKey = basketScreenFormatDateKey(placedOrder.pickUpDate)
                basketScreenLoadOrder(placedOrder.id, placedDateKey, forceLoad = true)

                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderSuccess = true
                            )
                        )
                    )
                }
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = error.message ?: "Bestellung fehlgeschlagen"
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            isCheckingOut = false,
                            orderError = e.message ?: "Ein Fehler ist aufgetreten"
                        )
                    )
                )
            }
        }
    }
}

internal fun BuyAppViewModel.basketScreenResetOrderState() {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                basketScreen = current.screens.basketScreen.copy(
                    orderSuccess = false,
                    orderError = null
                )
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenLoadOrder(orderId: String, date: String, forceLoad: Boolean = false) {
    viewModelScope.launch {
        println("🛒 BuyAppViewModel.basketScreenLoadOrder: START - orderId=$orderId, date=$date, forceLoad=$forceLoad")

        // Check for unsaved draft basket (only if not force loading after dialog confirmation)
        if (!forceLoad && basketScreenShowDraftWarningIfNeeded(orderId, date)) {
            println("🛒 BuyAppViewModel.basketScreenLoadOrder: Draft warning dialog shown, waiting for user decision")
            return@launch
        }

        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        isLoadingOrder = true,
                        orderError = null
                    )
                )
            )
        }

        try {
            val orderPath = "orders/$BASKET_SELLER_ID/$date/$orderId"
            val result = orderRepository.loadOrder(BASKET_SELLER_ID, orderId, orderPath)

            result.onSuccess { order ->
                val canEdit = OrderDateUtils.canEditOrder(
                    Instant.fromEpochMilliseconds(order.pickUpDate)
                )
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
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
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
                    )
                }
            }.onFailure { error ->
                // Clear loaded order info since loading failed
                basketRepository.clearBasket()
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = BasketScreenState(
                                isLoadingOrder = false,
                                orderError = "Bestellung konnte nicht geladen werden: ${error.message}",
                                availablePickupDates = current.screens.basketScreen.availablePickupDates
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            basketRepository.clearBasket()
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = BasketScreenState(
                            isLoadingOrder = false,
                            orderError = "Fehler beim Laden der Bestellung: ${e.message}",
                            availablePickupDates = current.screens.basketScreen.availablePickupDates
                        )
                    )
                )
            }
        }
    }
}

internal fun BuyAppViewModel.basketScreenEnableEditing() {
    val canEdit = _state.value.screens.basketScreen.canEdit
    if (canEdit) {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(isEditMode = true)
                )
            )
        }
    } else {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        orderError = "Bestellung kann nicht mehr bearbeitet werden (Frist: Dienstag 23:59)"
                    )
                )
            )
        }
    }
}

internal fun BuyAppViewModel.basketScreenUpdateOrder() {
    viewModelScope.launch {
        println("🛒 BuyAppViewModel.basketScreenUpdateOrder: START")
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        isCheckingOut = true,
                        orderError = null
                    )
                )
            )
        }

        try {
            val basketState = _state.value.screens.basketScreen
            val orderId = basketState.orderId
            val pickupDate = basketState.pickupDate
            val createdDate = basketState.createdDate

            if (orderId == null || pickupDate == null || createdDate == null) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = "Bestellinformationen fehlen"
                            )
                        )
                    )
                }
                return@launch
            }

            val canEdit = OrderDateUtils.canEditOrder(Instant.fromEpochMilliseconds(pickupDate))
            if (!canEdit) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = "Bearbeitungsfrist abgelaufen (Dienstag 23:59)"
                            )
                        )
                    )
                }
                return@launch
            }

            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = "Benutzer nicht angemeldet"
                            )
                        )
                    )
                }
                return@launch
            }

            val items = basketState.items
            if (items.isEmpty()) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = "Warenkorb ist leer"
                            )
                        )
                    )
                }
                return@launch
            }

            val buyerProfile = try {
                profileRepository.getBuyerProfile().getOrNull() ?: BuyerProfile(
                    id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false
                )
            } catch (e: Exception) {
                BuyerProfile(id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false)
            }

            val updatedOrder = Order(
                id = orderId,
                buyerProfile = buyerProfile,
                createdDate = createdDate,
                sellerId = BASKET_SELLER_ID,
                marketId = "",
                pickUpDate = pickupDate,
                message = "",
                articles = items
            )

            val result = orderRepository.updateOrder(updatedOrder)
            result.onSuccess {
                val currentItems = _state.value.screens.basketScreen.items
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderSuccess = true,
                                isEditMode = false,
                                originalOrderItems = currentItems,
                                hasChanges = false
                            )
                        )
                    )
                }
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = error.message ?: "Aktualisierung fehlgeschlagen"
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            isCheckingOut = false,
                            orderError = e.message ?: "Ein Fehler ist aufgetreten"
                        )
                    )
                )
            }
        }
    }
}

internal fun BuyAppViewModel.basketScreenCancelOrder() {
    viewModelScope.launch {
        println("🛒 BuyAppViewModel.basketScreenCancelOrder: START")
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        isCancelling = true,
                        orderError = null,
                        cancelSuccess = false
                    )
                )
            )
        }

        try {
            val basketState = _state.value.screens.basketScreen
            val orderId = basketState.orderId
            val orderDate = basketState.orderDate
            val pickupDate = basketState.pickupDate

            if (orderId == null || orderDate == null || pickupDate == null) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCancelling = false,
                                orderError = "Bestellinformationen fehlen"
                            )
                        )
                    )
                }
                return@launch
            }

            val canEdit = OrderDateUtils.canEditOrder(Instant.fromEpochMilliseconds(pickupDate))
            if (!canEdit) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCancelling = false,
                                orderError = "Stornierung nicht mehr möglich (Frist: Dienstag 23:59)"
                            )
                        )
                    )
                }
                return@launch
            }

            println("🛒 BuyAppViewModel.basketScreenCancelOrder: Calling orderRepository.cancelOrder")
            println("🛒 BuyAppViewModel.basketScreenCancelOrder: sellerId=$BASKET_SELLER_ID")
            println("🛒 BuyAppViewModel.basketScreenCancelOrder: orderDate=$orderDate")
            println("🛒 BuyAppViewModel.basketScreenCancelOrder: orderId=$orderId")
            val result = orderRepository.cancelOrder(BASKET_SELLER_ID, orderDate, orderId)

            if (result.isSuccess) {
                println("🛒 BuyAppViewModel.basketScreenCancelOrder: Cancel SUCCESS, clearing basket")
                // Clear basket in proper suspend context
                basketRepository.clearBasket()
                println("🛒 BuyAppViewModel.basketScreenCancelOrder: Basket cleared")

                // Remove cancelled order from buyer profile's placedOrderIds
                try {
                    val profile = profileRepository.getBuyerProfile().getOrNull()
                    if (profile != null && profile.placedOrderIds.containsValue(orderId)) {
                        val updatedOrderIds = profile.placedOrderIds.filterValues { it != orderId }
                        val updatedProfile = profile.copy(placedOrderIds = updatedOrderIds)
                        profileRepository.saveBuyerProfile(updatedProfile)
                        println("🛒 BuyAppViewModel.basketScreenCancelOrder: Removed cancelled order from buyer profile")
                    }
                } catch (e: Exception) {
                    println("🛒 BuyAppViewModel.basketScreenCancelOrder: Failed to update profile - ${e.message}")
                }

                println("🛒 BuyAppViewModel.basketScreenCancelOrder: Updating state")
                val availableDates = _state.value.screens.basketScreen.availablePickupDates
                _state.update { current ->
                    current.copy(
                        common = current.common.copy(
                            basket = current.common.basket.copy(
                                currentOrderId = null,
                                currentOrderDate = null
                            )
                        ),
                        screens = current.screens.copy(
                            basketScreen = BasketScreenState(
                                cancelSuccess = true,
                                availablePickupDates = availableDates
                            )
                        )
                    )
                }
                println("🛒 BuyAppViewModel.basketScreenCancelOrder: State updated to empty basket with cancelSuccess=true")
            } else {
                val error = result.exceptionOrNull()
                val errorMessage = error?.message ?: "Stornierung fehlgeschlagen"
                println("🛒 BuyAppViewModel.basketScreenCancelOrder: Cancel FAILED - $errorMessage")

                // If order not found, the order was already cancelled/deleted - clear basket and show empty state
                if (errorMessage.contains("not found", ignoreCase = true)) {
                    println("🛒 BuyAppViewModel.basketScreenCancelOrder: Order not found, clearing basket")
                    basketRepository.clearBasket()

                    // Also remove from buyer profile's placedOrderIds
                    try {
                        val profile = profileRepository.getBuyerProfile().getOrNull()
                        if (profile != null && profile.placedOrderIds.containsValue(orderId)) {
                            val updatedOrderIds = profile.placedOrderIds.filterValues { it != orderId }
                            val updatedProfile = profile.copy(placedOrderIds = updatedOrderIds)
                            profileRepository.saveBuyerProfile(updatedProfile)
                            println("🛒 BuyAppViewModel.basketScreenCancelOrder: Removed order from buyer profile")
                        }
                    } catch (e: Exception) {
                        println("🛒 BuyAppViewModel.basketScreenCancelOrder: Failed to update profile - ${e.message}")
                    }

                    val availableDates = _state.value.screens.basketScreen.availablePickupDates
                    _state.update { current ->
                        current.copy(
                            common = current.common.copy(
                                basket = current.common.basket.copy(
                                    currentOrderId = null,
                                    currentOrderDate = null
                                )
                            ),
                            screens = current.screens.copy(
                                basketScreen = BasketScreenState(
                                    availablePickupDates = availableDates
                                )
                            )
                        )
                    }
                } else {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCancelling = false,
                                    orderError = errorMessage
                                )
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            isCancelling = false,
                            orderError = e.message ?: "Ein Fehler ist aufgetreten"
                        )
                    )
                )
            }
        }
    }
}

internal fun BuyAppViewModel.basketScreenLoadAvailableDates() {
    println("📅 BuyAppViewModel.basketScreenLoadAvailableDates: START")
    val dates = OrderDateUtils.getAvailablePickupDates(count = 5)
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                basketScreen = current.screens.basketScreen.copy(
                    availablePickupDates = dates.map { it.toEpochMilliseconds() }
                )
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenShowDatePicker() {
    if (_state.value.screens.basketScreen.availablePickupDates.isEmpty()) {
        basketScreenLoadAvailableDates()
    }
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                basketScreen = current.screens.basketScreen.copy(showDatePicker = true)
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenHideDatePicker() {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                basketScreen = current.screens.basketScreen.copy(showDatePicker = false)
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenSelectPickupDate(date: Long) {
    val isValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(date))
    if (!isValid) {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        orderError = "Gewähltes Datum ist nicht mehr verfügbar.",
                        selectedPickupDate = null,
                        showDatePicker = true
                    )
                )
            )
        }
        basketScreenLoadAvailableDates()
        return
    }

    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                basketScreen = current.screens.basketScreen.copy(
                    selectedPickupDate = date,
                    showDatePicker = false,
                    orderError = null
                )
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenShowReorderDatePicker() {
    if (_state.value.screens.basketScreen.availablePickupDates.isEmpty()) {
        basketScreenLoadAvailableDates()
    }
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                basketScreen = current.screens.basketScreen.copy(showReorderDatePicker = true)
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenHideReorderDatePicker() {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                basketScreen = current.screens.basketScreen.copy(showReorderDatePicker = false)
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenReorderWithNewDate(newPickupDate: Long, currentArticles: List<Article>) {
    viewModelScope.launch {
        println("🛒 BuyAppViewModel.basketScreenReorderWithNewDate: START")
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        isReordering = true,
                        showReorderDatePicker = false,
                        orderError = null,
                        reorderSuccess = false
                    )
                )
            )
        }

        try {
            val isDateValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(newPickupDate))
            if (!isDateValid) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isReordering = false,
                                orderError = "Gewähltes Datum ist nicht mehr verfügbar.",
                                showReorderDatePicker = true
                            )
                        )
                    )
                }
                basketScreenLoadAvailableDates()
                return@launch
            }

            val currentItems = _state.value.screens.basketScreen.items
            if (currentItems.isEmpty()) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isReordering = false,
                                orderError = "Keine Artikel zum Nachbestellen"
                            )
                        )
                    )
                }
                return@launch
            }

            val updatedItems = mutableListOf<OrderedProduct>()
            for (item in currentItems) {
                val article = currentArticles.find { it.id == item.id }
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
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
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
                )
            }
        } catch (e: Exception) {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            isReordering = false,
                            orderError = e.message ?: "Ein Fehler ist aufgetreten"
                        )
                    )
                )
            }
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
            screens = current.screens.copy(
                basketScreen = current.screens.basketScreen.copy(
                    showMergeDialog = false,
                    existingOrderForMerge = null,
                    mergeConflicts = emptyList()
                )
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenResolveMergeConflict(productId: String, resolution: MergeResolution) {
    val updatedConflicts = _state.value.screens.basketScreen.mergeConflicts.map { conflict ->
        if (conflict.productId == productId) conflict.copy(resolution = resolution) else conflict
    }
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                basketScreen = current.screens.basketScreen.copy(mergeConflicts = updatedConflicts)
            )
        )
    }
}

internal fun BuyAppViewModel.basketScreenConfirmMerge() {
    viewModelScope.launch {
        println("🔀 BuyAppViewModel.basketScreenConfirmMerge: START")
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(isMerging = true)
                )
            )
        }

        try {
            val basketState = _state.value.screens.basketScreen
            val existingOrder = basketState.existingOrderForMerge

            if (existingOrder == null) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isMerging = false,
                                orderError = "Keine bestehende Bestellung zum Zusammenführen"
                            )
                        )
                    )
                }
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
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isMerging = false,
                                orderError = "Benutzer nicht angemeldet"
                            )
                        )
                    )
                }
                return@launch
            }

            val buyerProfile = try {
                profileRepository.getBuyerProfile().getOrNull() ?: BuyerProfile(
                    id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false
                )
            } catch (e: Exception) {
                BuyerProfile(id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false)
            }

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
                    println("🔀 basketScreenConfirmMerge: Cleared draft basket after merge")
                } catch (e: Exception) {
                    println("⚠️ basketScreenConfirmMerge: Failed to clear draft basket: ${e.message}")
                }

                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
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
                    )
                }
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isMerging = false,
                                orderError = "Zusammenführung fehlgeschlagen: ${error.message}"
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            isMerging = false,
                            orderError = "Ein Fehler ist aufgetreten: ${e.message}"
                        )
                    )
                )
            }
        }
    }
}

internal fun BuyAppViewModel.basketScreenFormatDateKey(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val year = dateTime.year
    val month = dateTime.month.number.toString().padStart(2, '0')
    val day = dateTime.day.toString().padStart(2, '0')
    return "$year$month$day"
}

/**
 * Format timestamp to readable date string for BasketScreen
 */
internal fun BuyAppViewModel.basketScreenFormatDate(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = dateTime.day.toString().padStart(2, '0')
    val month = dateTime.month.number.toString().padStart(2, '0')
    val year = dateTime.year
    return "$day.$month.$year"
}

/**
 * Check if order can be edited based on pickup date
 */
internal fun BuyAppViewModel.basketScreenCanEditOrder(pickupDate: Long): Boolean {
    return OrderDateUtils.canEditOrder(Instant.fromEpochMilliseconds(pickupDate))
}

/**
 * Get days until pickup for BasketScreen
 */
internal fun BuyAppViewModel.basketScreenGetDaysUntilPickup(pickupDate: Long): Long {
    val diff = pickupDate - Clock.System.now().toEpochMilliseconds()
    return diff / (24 * 60 * 60 * 1000)
}

// ===== Draft Warning Dialog Functions =====

/**
 * Hide the draft warning dialog without taking any action
 */
internal fun BuyAppViewModel.basketScreenHideDraftWarningDialog() {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                basketScreen = current.screens.basketScreen.copy(
                    showDraftWarningDialog = false,
                    draftItemCount = 0,
                    pendingOrderIdForLoad = null,
                    pendingOrderDateForLoad = null
                )
            )
        )
    }
}

/**
 * Save the current draft to profile and then load the pending order
 */
internal fun BuyAppViewModel.basketScreenSaveDraftAndLoadOrder() {
    viewModelScope.launch {
        val pendingOrderId = _state.value.screens.basketScreen.pendingOrderIdForLoad
        val pendingOrderDate = _state.value.screens.basketScreen.pendingOrderDateForLoad

        if (pendingOrderId == null || pendingOrderDate == null) {
            println("⚠️ basketScreenSaveDraftAndLoadOrder: No pending order info")
            basketScreenHideDraftWarningDialog()
            return@launch
        }

        // Save current draft to profile
        try {
            saveDraftBasketToProfile()
            println("✅ basketScreenSaveDraftAndLoadOrder: Draft saved successfully")
        } catch (e: Exception) {
            println("⚠️ basketScreenSaveDraftAndLoadOrder: Failed to save draft: ${e.message}")
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
        val pendingOrderId = _state.value.screens.basketScreen.pendingOrderIdForLoad
        val pendingOrderDate = _state.value.screens.basketScreen.pendingOrderDateForLoad

        if (pendingOrderId == null || pendingOrderDate == null) {
            println("⚠️ basketScreenDiscardDraftAndLoadOrder: No pending order info")
            basketScreenHideDraftWarningDialog()
            return@launch
        }

        // Clear the draft from profile
        try {
            profileRepository.clearDraftBasket()
            println("✅ basketScreenDiscardDraftAndLoadOrder: Draft cleared")
        } catch (e: Exception) {
            println("⚠️ basketScreenDiscardDraftAndLoadOrder: Failed to clear draft: ${e.message}")
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
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        showDraftWarningDialog = true,
                        draftItemCount = draftItemCount,
                        pendingOrderIdForLoad = orderId,
                        pendingOrderDateForLoad = date
                    )
                )
            )
        }
        return true
    }
    return false
}
