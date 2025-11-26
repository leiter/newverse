package com.together.newverse.ui.screens.buy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Actions that can be performed on the Basket screen
 */
sealed interface BasketAction {
    data class AddItem(val item: OrderedProduct) : BasketAction
    data class RemoveItem(val productId: String) : BasketAction
    data class UpdateQuantity(val productId: String, val newQuantity: Double) : BasketAction
    data object ClearBasket : BasketAction
    data object Checkout : BasketAction
    data class LoadOrder(val orderId: String, val date: String) : BasketAction
    data object UpdateOrder : BasketAction
    data object EnableEditing : BasketAction

    // Pickup date selection actions
    data object ShowDatePicker : BasketAction
    data object HideDatePicker : BasketAction
    data class SelectPickupDate(val date: Long) : BasketAction
    data object LoadAvailableDates : BasketAction

    // Cancel order
    data object CancelOrder : BasketAction

    // Reorder with new date
    data object ShowReorderDatePicker : BasketAction
    data object HideReorderDatePicker : BasketAction
    data class ReorderWithNewDate(val newPickupDate: Long, val currentArticles: List<Article>) : BasketAction
}

/**
 * State for the Basket screen
 */
data class BasketScreenState(
    val items: List<OrderedProduct> = emptyList(),
    val total: Double = 0.0,
    val isCheckingOut: Boolean = false,
    val orderSuccess: Boolean = false,
    val orderError: String? = null,
    // Order viewing/editing
    val orderId: String? = null,
    val orderDate: String? = null,
    val pickupDate: Long? = null,
    val createdDate: Long? = null,
    val isEditMode: Boolean = false,
    val canEdit: Boolean = false,
    val isLoadingOrder: Boolean = false,
    // Track if order has been modified
    val originalOrderItems: List<OrderedProduct> = emptyList(),
    val hasChanges: Boolean = false,
    // Pickup date selection for draft orders
    val selectedPickupDate: Long? = null,
    val availablePickupDates: List<Long> = emptyList(),
    val showDatePicker: Boolean = false,
    // Cancel order
    val isCancelling: Boolean = false,
    val cancelSuccess: Boolean = false,
    // Reorder with new date
    val showReorderDatePicker: Boolean = false,
    val isReordering: Boolean = false,
    val reorderSuccess: Boolean = false
)

/**
 * ViewModel for Shopping Basket screen
 */
class BasketViewModel(
    private val basketRepository: BasketRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BasketScreenState())
    val state: StateFlow<BasketScreenState> = _state.asStateFlow()

    // Hardcoded seller ID - in production this should come from app configuration or first seller lookup
    // This matches the universe project where there's typically a single seller
    private val SELLER_ID = "" // Will be looked up from Firebase

    init {
        // Observe basket changes from repository
        viewModelScope.launch {
            basketRepository.observeBasket().collect { items ->
                val hasChanges = checkIfBasketHasChanges(items, _state.value.originalOrderItems)
                _state.value = _state.value.copy(
                    items = items,
                    total = basketRepository.getTotal(),
                    hasChanges = hasChanges
                )
            }
        }

        // Load available pickup dates for draft orders
        viewModelScope.launch {
            loadAvailableDates()
        }

        // Auto-load the most recent editable order if it exists
        viewModelScope.launch {
            loadMostRecentEditableOrder()
        }
    }

    /**
     * Load the most recent editable order for the current user
     * Only loads if BasketRepository doesn't already have an order loaded
     */
    private suspend fun loadMostRecentEditableOrder() {
        try {
            println("🛒 BasketViewModel.loadMostRecentEditableOrder: START")

            // Check if BasketRepository already has an order loaded
            val loadedOrderInfo = basketRepository.getLoadedOrderInfo()
            if (loadedOrderInfo != null) {
                val (orderId, orderDate) = loadedOrderInfo
                println("🛒 BasketViewModel.loadMostRecentEditableOrder: Order already loaded in basket - orderId=$orderId, date=$orderDate")

                // Sync the loaded order info to our state
                val result = orderRepository.loadOrder(SELLER_ID, orderDate, orderId)
                result.onSuccess { order ->
                    val threeDaysBeforePickup = order.pickUpDate - (3 * 24 * 60 * 60 * 1000)
                    val canEdit = Clock.System.now().toEpochMilliseconds() < threeDaysBeforePickup

                    // Get current basket items from repository (may have been modified on MainScreen)
                    val currentBasketItems = basketRepository.observeBasket().value

                    // Check if basket has changes compared to original order
                    val hasChanges = checkIfBasketHasChanges(currentBasketItems, order.articles)

                    _state.value = _state.value.copy(
                        orderId = orderId,
                        orderDate = orderDate,
                        pickupDate = order.pickUpDate,
                        createdDate = order.createdDate,
                        isEditMode = false,
                        canEdit = canEdit,
                        originalOrderItems = order.articles,
                        hasChanges = hasChanges  // ✅ Calculate from current basket state
                    )
                    println("🛒 BasketViewModel.loadMostRecentEditableOrder: Synced state with loaded order - hasChanges=$hasChanges")
                }
                return
            }

            // Get buyer profile to access placedOrderIds
            val profileResult = profileRepository.getBuyerProfile()
            val buyerProfile = profileResult.getOrNull()

            if (buyerProfile == null || buyerProfile.placedOrderIds.isEmpty()) {
                println("🛒 BasketViewModel.loadMostRecentEditableOrder: No buyer profile or orders found")
                return
            }

            println("🛒 BasketViewModel.loadMostRecentEditableOrder: Found ${buyerProfile.placedOrderIds.size} placed orders")

            // Get the most recent editable order
            val orderResult = orderRepository.getOpenEditableOrder(SELLER_ID, buyerProfile.placedOrderIds)
            val order = orderResult.getOrNull()

            if (order != null) {
                println("🛒 BasketViewModel.loadMostRecentEditableOrder: Found editable order - orderId=${order.id}")

                // Calculate date key
                val dateKey = formatDateKey(order.pickUpDate)

                // Load the order (this will populate the basket)
                loadOrder(order.id, dateKey)
            } else {
                println("🛒 BasketViewModel.loadMostRecentEditableOrder: No editable orders found")
            }
        } catch (e: Exception) {
            println("❌ BasketViewModel.loadMostRecentEditableOrder: Error - ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Check if current basket items differ from original order items
     */
    private fun checkIfBasketHasChanges(currentItems: List<OrderedProduct>, originalItems: List<OrderedProduct>): Boolean {
        // If there's no original order, no changes to track
        if (originalItems.isEmpty() && currentItems.isEmpty()) return false
        if (originalItems.isEmpty()) return true

        // Different number of items = changed
        if (currentItems.size != originalItems.size) return true

        // Check if any item quantity or content changed
        currentItems.forEach { currentItem ->
            val originalItem = originalItems.find { it.productId == currentItem.productId }
            if (originalItem == null) {
                // New item added
                return true
            }
            if (originalItem.amountCount != currentItem.amountCount) {
                // Quantity changed
                return true
            }
        }

        // Check if any original items were removed
        originalItems.forEach { originalItem ->
            val currentItem = currentItems.find { it.productId == originalItem.productId }
            if (currentItem == null) {
                // Item removed
                return true
            }
        }

        return false
    }

    fun onAction(action: BasketAction) {
        when (action) {
            is BasketAction.AddItem -> addItem(action.item)
            is BasketAction.RemoveItem -> removeItem(action.productId)
            is BasketAction.UpdateQuantity -> updateQuantity(action.productId, action.newQuantity)
            BasketAction.ClearBasket -> clearBasket()
            BasketAction.Checkout -> checkout()
            is BasketAction.LoadOrder -> loadOrder(action.orderId, action.date, forceLoad = true)
            BasketAction.UpdateOrder -> updateOrder()
            BasketAction.EnableEditing -> enableEditing()
            // Pickup date selection actions
            BasketAction.ShowDatePicker -> showDatePicker()
            BasketAction.HideDatePicker -> hideDatePicker()
            is BasketAction.SelectPickupDate -> selectPickupDate(action.date)
            BasketAction.LoadAvailableDates -> loadAvailableDates()
            // Cancel order
            BasketAction.CancelOrder -> cancelOrder()
            // Reorder with new date
            BasketAction.ShowReorderDatePicker -> showReorderDatePicker()
            BasketAction.HideReorderDatePicker -> hideReorderDatePicker()
            is BasketAction.ReorderWithNewDate -> reorderWithNewDate(action.newPickupDate, action.currentArticles)
        }
    }

    private fun addItem(item: OrderedProduct) {
        viewModelScope.launch {
            basketRepository.addItem(item)
        }
    }

    private fun removeItem(productId: String) {
        viewModelScope.launch {
            basketRepository.removeItem(productId)
        }
    }

    private fun updateQuantity(productId: String, newQuantity: Double) {
        viewModelScope.launch {
            basketRepository.updateQuantity(productId, newQuantity)
        }
    }

    private fun clearBasket() {
        viewModelScope.launch {
            basketRepository.clearBasket()
        }
    }

    private fun checkout() {
        viewModelScope.launch {
            println("🛒 BasketViewModel.checkout: START")
            _state.value = _state.value.copy(
                isCheckingOut = true,
                orderSuccess = false,
                orderError = null
            )

            try {
                // Get current user ID
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    println("❌ BasketViewModel.checkout: User not authenticated")
                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderError = "Bitte melden Sie sich an, um eine Bestellung aufzugeben"
                    )
                    return@launch
                }

                // Get basket items
                val items = _state.value.items
                if (items.isEmpty()) {
                    println("❌ BasketViewModel.checkout: Basket is empty")
                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderError = "Warenkorb ist leer"
                    )
                    return@launch
                }

                // Get buyer profile from repository
                val buyerProfile = try {
                    val profileResult = profileRepository.getBuyerProfile()
                    profileResult.getOrNull() ?: BuyerProfile(
                        id = currentUserId,
                        displayName = "Kunde",
                        emailAddress = "",
                        anonymous = false
                    )
                } catch (e: Exception) {
                    println("⚠️ BasketViewModel.checkout: Could not load buyer profile, using defaults - ${e.message}")
                    BuyerProfile(
                        id = currentUserId,
                        displayName = "Kunde",
                        emailAddress = "",
                        anonymous = false
                    )
                }

                println("🛒 BasketViewModel.checkout: Using buyer profile - name=${buyerProfile.displayName}, email=${buyerProfile.emailAddress}")

                // NEW: Check if pickup date has been selected
                val selectedDate = _state.value.selectedPickupDate
                if (selectedDate == null) {
                    println("❌ BasketViewModel.checkout: No pickup date selected")
                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderError = "Bitte wählen Sie ein Abholdatum",
                        showDatePicker = true  // Automatically show picker
                    )
                    return@launch
                }

                // NEW: Validate selected date is still valid
                val isDateValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(selectedDate))
                if (!isDateValid) {
                    println("❌ BasketViewModel.checkout: Selected date is no longer valid")
                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderError = "Gewähltes Datum ist nicht mehr verfügbar. Bitte wählen Sie ein neues Datum.",
                        selectedPickupDate = null,
                        showDatePicker = true
                    )
                    // Reload available dates
                    loadAvailableDates()
                    return@launch
                }

                // Use the selected pickup date
                val pickUpDate = selectedDate
                println("🛒 BasketViewModel.checkout: Using selected pickup date: ${formatDate(pickUpDate)}")
                println("🛒   createdDate: ${Clock.System.now().toEpochMilliseconds()} (today)")
                println("🛒   pickUpDate: $pickUpDate (${formatDate(pickUpDate)})")

                val dateKey = formatDateKey(pickUpDate)

                // Check if there's already an order for this date
                val existingOrderId = buyerProfile.placedOrderIds[dateKey]
                if (existingOrderId != null) {
                    println("⚠️ BasketViewModel.checkout: Order already exists for date $dateKey - orderId=$existingOrderId")

                    // Load the existing order instead of creating a new one
                    loadOrder(existingOrderId, dateKey)

                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderError = "Für dieses Datum existiert bereits eine Bestellung. Sie können diese bearbeiten."
                    )
                    return@launch
                }

                // Create order with today's date and selected pickup date
                val order = Order(
                    buyerProfile = buyerProfile,
                    createdDate = Clock.System.now().toEpochMilliseconds(),
                    sellerId = SELLER_ID,
                    marketId = "", // Default market
                    pickUpDate = pickUpDate, // User-selected Thursday
                    message = "",
                    articles = items
                )

                println("🛒 BasketViewModel.checkout: Placing order with ${items.size} items, total=${_state.value.total}")

                // Place order via repository
                val result = orderRepository.placeOrder(order)

                result.onSuccess { placedOrder ->
                    println("✅ BasketViewModel.checkout: Order placed successfully - orderId=${placedOrder.id}")

                    // Calculate date key for the order
                    val dateKey = formatDateKey(placedOrder.pickUpDate)

                    // Load the order so user can see it (this also updates basket repository)
                    loadOrder(placedOrder.id, dateKey)

                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderSuccess = true
                    )
                }.onFailure { error ->
                    println("❌ BasketViewModel.checkout: Order placement failed - ${error.message}")
                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderError = error.message ?: "Bestellung fehlgeschlagen"
                    )
                }

            } catch (e: Exception) {
                println("❌ BasketViewModel.checkout: Exception - ${e.message}")
                _state.value = _state.value.copy(
                    isCheckingOut = false,
                    orderError = e.message ?: "Ein Fehler ist aufgetreten"
                )
            }
        }
    }

    /**
     * Reset order state after displaying success/error
     */
    fun resetOrderState() {
        _state.value = _state.value.copy(
            orderSuccess = false,
            orderError = null
        )
    }

    /**
     * Load an existing order for viewing/editing
     * @param forceLoad If true, always load the order's items (used when user explicitly selects an order)
     */
    private fun loadOrder(orderId: String, date: String, forceLoad: Boolean = false) {
        viewModelScope.launch {
            println("🛒 BasketViewModel.loadOrder: START - orderId=$orderId, date=$date, forceLoad=$forceLoad")
            _state.value = _state.value.copy(
                isLoadingOrder = true,
                orderError = null
            )

            try {
                val result = orderRepository.loadOrder(SELLER_ID, date, orderId)

                result.onSuccess { order ->
                    println("✅ BasketViewModel.loadOrder: Order loaded successfully")

                    // Calculate if order can still be edited (3 days before pickup)
                    val threeDaysBeforePickup = order.pickUpDate - (3 * 24 * 60 * 60 * 1000)
                    val canEdit = Clock.System.now().toEpochMilliseconds() < threeDaysBeforePickup

                    // Get current basket items BEFORE loading order
                    val currentBasketItems = basketRepository.observeBasket().value

                    // Load order items if:
                    // - forceLoad is true (user explicitly selected this order), OR
                    // - basket is empty, OR
                    // - basket has a different order loaded
                    val shouldLoadOrderItems = forceLoad ||
                        currentBasketItems.isEmpty() ||
                        basketRepository.getLoadedOrderInfo()?.first != orderId

                    if (shouldLoadOrderItems) {
                        println("🛒 BasketViewModel.loadOrder: Loading order items into basket (forceLoad=$forceLoad)")
                        basketRepository.loadOrderItems(order.articles, orderId, date)
                    } else {
                        println("🛒 BasketViewModel.loadOrder: Basket already has items, preserving user modifications")
                    }

                    // Get final basket items (either just loaded or existing with modifications)
                    val finalBasketItems = basketRepository.observeBasket().value

                    // Check if there are changes compared to original order
                    val hasChanges = checkIfBasketHasChanges(finalBasketItems, order.articles)

                    _state.value = _state.value.copy(
                        orderId = orderId,
                        orderDate = date,
                        pickupDate = order.pickUpDate,
                        createdDate = order.createdDate,
                        isEditMode = false,
                        canEdit = canEdit,
                        isLoadingOrder = false,
                        items = finalBasketItems,  // ✅ Use actual basket items, not original order
                        total = finalBasketItems.sumOf { it.price * it.amountCount },
                        originalOrderItems = order.articles,
                        hasChanges = hasChanges  // ✅ Calculate from actual basket state
                    )
                    println("🛒 BasketViewModel.loadOrder: State updated - hasChanges=$hasChanges, items=${finalBasketItems.size}")
                }.onFailure { error ->
                    println("❌ BasketViewModel.loadOrder: Failed - ${error.message}")
                    _state.value = _state.value.copy(
                        isLoadingOrder = false,
                        orderError = "Bestellung konnte nicht geladen werden: ${error.message}"
                    )
                }
            } catch (e: Exception) {
                println("❌ BasketViewModel.loadOrder: Exception - ${e.message}")
                _state.value = _state.value.copy(
                    isLoadingOrder = false,
                    orderError = "Fehler beim Laden der Bestellung: ${e.message}"
                )
            }
        }
    }

    /**
     * Enable editing mode for the current order
     */
    private fun enableEditing() {
        if (_state.value.canEdit) {
            _state.value = _state.value.copy(isEditMode = true)
            println("🛒 BasketViewModel.enableEditing: Edit mode enabled")
        } else {
            println("⚠️ BasketViewModel.enableEditing: Cannot edit - deadline passed")
            _state.value = _state.value.copy(
                orderError = "Bestellung kann nicht mehr bearbeitet werden (weniger als 3 Tage bis Abholung)"
            )
        }
    }

    /**
     * Update an existing order with current basket items
     */
    private fun updateOrder() {
        viewModelScope.launch {
            println("🛒 BasketViewModel.updateOrder: START")
            _state.value = _state.value.copy(
                isCheckingOut = true,
                orderError = null
            )

            try {
                val orderId = _state.value.orderId
                val pickupDate = _state.value.pickupDate
                val createdDate = _state.value.createdDate

                if (orderId == null || pickupDate == null || createdDate == null) {
                    println("❌ BasketViewModel.updateOrder: Missing order information")
                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderError = "Bestellinformationen fehlen"
                    )
                    return@launch
                }

                // Check if still within edit deadline
                val threeDaysBeforePickup = pickupDate - (3 * 24 * 60 * 60 * 1000)
                if (Clock.System.now().toEpochMilliseconds() >= threeDaysBeforePickup) {
                    println("❌ BasketViewModel.updateOrder: Edit deadline passed")
                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderError = "Bearbeitungsfrist abgelaufen (weniger als 3 Tage bis Abholung)"
                    )
                    return@launch
                }

                // Get current user
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    println("❌ BasketViewModel.updateOrder: User not authenticated")
                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderError = "Benutzer nicht angemeldet"
                    )
                    return@launch
                }

                // Get basket items
                val items = _state.value.items
                if (items.isEmpty()) {
                    println("❌ BasketViewModel.updateOrder: Basket is empty")
                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderError = "Warenkorb ist leer"
                    )
                    return@launch
                }

                // Get buyer profile from repository
                val buyerProfile = try {
                    val profileResult = profileRepository.getBuyerProfile()
                    profileResult.getOrNull() ?: BuyerProfile(
                        id = currentUserId,
                        displayName = "Kunde",
                        emailAddress = "",
                        anonymous = false
                    )
                } catch (e: Exception) {
                    println("⚠️ BasketViewModel.updateOrder: Could not load buyer profile, using defaults - ${e.message}")
                    BuyerProfile(
                        id = currentUserId,
                        displayName = "Kunde",
                        emailAddress = "",
                        anonymous = false
                    )
                }

                println("🛒 BasketViewModel.updateOrder: Using buyer profile - name=${buyerProfile.displayName}, email=${buyerProfile.emailAddress}")

                // Create updated order
                val updatedOrder = Order(
                    id = orderId,
                    buyerProfile = buyerProfile,
                    createdDate = createdDate,
                    sellerId = SELLER_ID,
                    marketId = "",
                    pickUpDate = pickupDate,
                    message = "",
                    articles = items
                )

                println("🛒 BasketViewModel.updateOrder: Updating order with ${items.size} items, total=${_state.value.total}")

                // Update order via repository
                val result = orderRepository.updateOrder(updatedOrder)

                result.onSuccess {
                    println("✅ BasketViewModel.updateOrder: Order updated successfully")

                    // Update original order items to current items after successful update
                    val currentItems = _state.value.items
                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderSuccess = true,
                        isEditMode = false,
                        originalOrderItems = currentItems,
                        hasChanges = false
                    )
                }.onFailure { error ->
                    println("❌ BasketViewModel.updateOrder: Update failed - ${error.message}")
                    _state.value = _state.value.copy(
                        isCheckingOut = false,
                        orderError = error.message ?: "Aktualisierung fehlgeschlagen"
                    )
                }

            } catch (e: Exception) {
                println("❌ BasketViewModel.updateOrder: Exception - ${e.message}")
                _state.value = _state.value.copy(
                    isCheckingOut = false,
                    orderError = e.message ?: "Ein Fehler ist aufgetreten"
                )
            }
        }
    }

    /**
     * Cancel an existing order
     */
    private fun cancelOrder() {
        viewModelScope.launch {
            println("🛒 BasketViewModel.cancelOrder: START")
            _state.value = _state.value.copy(
                isCancelling = true,
                orderError = null,
                cancelSuccess = false
            )

            try {
                val orderId = _state.value.orderId
                val orderDate = _state.value.orderDate
                val pickupDate = _state.value.pickupDate

                if (orderId == null || orderDate == null || pickupDate == null) {
                    println("❌ BasketViewModel.cancelOrder: Missing order information")
                    _state.value = _state.value.copy(
                        isCancelling = false,
                        orderError = "Bestellinformationen fehlen"
                    )
                    return@launch
                }

                // Check if still within edit deadline
                val threeDaysBeforePickup = pickupDate - (3 * 24 * 60 * 60 * 1000)
                if (Clock.System.now().toEpochMilliseconds() >= threeDaysBeforePickup) {
                    println("❌ BasketViewModel.cancelOrder: Edit deadline passed")
                    _state.value = _state.value.copy(
                        isCancelling = false,
                        orderError = "Stornierung nicht mehr möglich (weniger als 3 Tage bis Abholung)"
                    )
                    return@launch
                }

                println("🛒 BasketViewModel.cancelOrder: Cancelling order - orderId=$orderId, date=$orderDate")

                // Cancel order via repository
                val result = orderRepository.cancelOrder(SELLER_ID, orderDate, orderId)

                result.onSuccess {
                    println("✅ BasketViewModel.cancelOrder: Order cancelled successfully")

                    // Clear the basket
                    basketRepository.clearBasket()

                    // Reset state to show empty basket (new order mode)
                    _state.value = BasketScreenState(
                        cancelSuccess = true,
                        availablePickupDates = _state.value.availablePickupDates
                    )
                }.onFailure { error ->
                    println("❌ BasketViewModel.cancelOrder: Cancel failed - ${error.message}")
                    _state.value = _state.value.copy(
                        isCancelling = false,
                        orderError = error.message ?: "Stornierung fehlgeschlagen"
                    )
                }

            } catch (e: Exception) {
                println("❌ BasketViewModel.cancelOrder: Exception - ${e.message}")
                _state.value = _state.value.copy(
                    isCancelling = false,
                    orderError = e.message ?: "Ein Fehler ist aufgetreten"
                )
            }
        }
    }

    // ===== Reorder Functions =====

    /**
     * Show the date picker for reordering
     */
    private fun showReorderDatePicker() {
        println("📅 BasketViewModel.showReorderDatePicker")
        // Ensure dates are loaded
        if (_state.value.availablePickupDates.isEmpty()) {
            loadAvailableDates()
        }
        _state.value = _state.value.copy(showReorderDatePicker = true)
    }

    /**
     * Hide the reorder date picker
     */
    private fun hideReorderDatePicker() {
        println("📅 BasketViewModel.hideReorderDatePicker")
        _state.value = _state.value.copy(showReorderDatePicker = false)
    }

    /**
     * Create a new order from the current order with a new pickup date and updated prices
     * @param newPickupDate The new pickup date timestamp
     * @param currentArticles The list of currently loaded articles with current prices
     */
    private fun reorderWithNewDate(newPickupDate: Long, currentArticles: List<Article>) {
        viewModelScope.launch {
            println("🛒 BasketViewModel.reorderWithNewDate: START - newPickupDate=${formatDate(newPickupDate)}, articles=${currentArticles.size}")
            _state.value = _state.value.copy(
                isReordering = true,
                showReorderDatePicker = false,
                orderError = null,
                reorderSuccess = false
            )

            try {
                // Validate the selected date is still valid
                val isDateValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(newPickupDate))
                if (!isDateValid) {
                    println("❌ BasketViewModel.reorderWithNewDate: Selected date is no longer valid")
                    _state.value = _state.value.copy(
                        isReordering = false,
                        orderError = "Gewähltes Datum ist nicht mehr verfügbar. Bitte wählen Sie ein neues Datum.",
                        showReorderDatePicker = true
                    )
                    loadAvailableDates()
                    return@launch
                }

                // Get current items from the order
                val currentItems = _state.value.items
                if (currentItems.isEmpty()) {
                    println("❌ BasketViewModel.reorderWithNewDate: No items to reorder")
                    _state.value = _state.value.copy(
                        isReordering = false,
                        orderError = "Keine Artikel zum Nachbestellen vorhanden"
                    )
                    return@launch
                }

                println("🛒 BasketViewModel.reorderWithNewDate: Updating prices for ${currentItems.size} items from ${currentArticles.size} loaded articles")

                // Debug: Log available article IDs
                if (currentArticles.isEmpty()) {
                    println("   ⚠️ WARNING: currentArticles is EMPTY! Prices cannot be updated.")
                } else {
                    println("   📋 Available articles (id/productId/name): ${currentArticles.take(5).map { "${it.id}/${it.productId}/${it.productName}" }}")
                }

                // Update prices from the already-loaded articles list
                val updatedItems = mutableListOf<OrderedProduct>()
                var pricesUpdated = 0

                for (item in currentItems) {
                    println("   🔍 OrderedProduct: $item")
                    // Find the article in the already-loaded list
                    // Match Firebase ID: OrderedProduct.id with Article.id
                    val article = currentArticles.find { it.id == item.id }
                    println("   🔍 Found article: $article")

                    if (article != null && article.available) {
                        val updatedItem = item.copy(
                            price = article.price,
                            productName = article.productName,
                            unit = article.unit
                        )
                        updatedItems.add(updatedItem)
                        if (item.price != article.price) {
                            println("   💰 Price updated: ${item.productName} ${item.price} → ${article.price}")
                            pricesUpdated++
                        }
                    } else if (article != null && !article.available) {
                        println("   ⚠️ Article no longer available: ${item.productName}")
                        // Still add it but with old price - user can remove it manually
                        updatedItems.add(item)
                    } else {
                        println("   ⚠️ Article not found in loaded list: ${item.productName} - using old price")
                        // Keep old price if article is not in the loaded list
                        updatedItems.add(item)
                    }
                }

                println("🛒 BasketViewModel.reorderWithNewDate: Updated $pricesUpdated prices")

                // Clear basket and load updated items
                basketRepository.clearBasket()
                for (item in updatedItems) {
                    basketRepository.addItem(item)
                }

                // Calculate new total with updated prices
                val newTotal = updatedItems.sumOf { it.price * it.amountCount }

                // Reset state to new order mode with selected pickup date and updated items
                _state.value = _state.value.copy(
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

                println("✅ BasketViewModel.reorderWithNewDate: Reorder prepared successfully - newTotal=$newTotal")
            } catch (e: Exception) {
                println("❌ BasketViewModel.reorderWithNewDate: Exception - ${e.message}")
                _state.value = _state.value.copy(
                    isReordering = false,
                    orderError = e.message ?: "Ein Fehler ist aufgetreten"
                )
            }
        }
    }

    // ===== Pickup Date Selection Functions =====

    /**
     * Load available pickup dates (Thursdays where deadline hasn't passed)
     */
    private fun loadAvailableDates() {
        println("📅 BasketViewModel.loadAvailableDates: START")
        val dates = OrderDateUtils.getAvailablePickupDates(count = 5)
        _state.value = _state.value.copy(
            availablePickupDates = dates.map { it.toEpochMilliseconds() }
        )
        println("📅 BasketViewModel.loadAvailableDates: Loaded ${dates.size} available dates")
    }

    /**
     * Show date picker dialog
     */
    private fun showDatePicker() {
        println("📅 BasketViewModel.showDatePicker")
        // Ensure dates are loaded
        if (_state.value.availablePickupDates.isEmpty()) {
            loadAvailableDates()
        }
        _state.value = _state.value.copy(showDatePicker = true)
    }

    /**
     * Hide date picker dialog
     */
    private fun hideDatePicker() {
        println("📅 BasketViewModel.hideDatePicker")
        _state.value = _state.value.copy(showDatePicker = false)
    }

    /**
     * Select a pickup date
     */
    private fun selectPickupDate(date: Long) {
        println("📅 BasketViewModel.selectPickupDate: Selected ${formatDate(date)}")

        // Validate the selected date is still valid
        val isValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(date))

        if (!isValid) {
            println("❌ BasketViewModel.selectPickupDate: Selected date is no longer valid")
            _state.value = _state.value.copy(
                orderError = "Gewähltes Datum ist nicht mehr verfügbar. Bitte wählen Sie ein neues Datum.",
                selectedPickupDate = null,
                showDatePicker = true
            )
            // Reload dates
            loadAvailableDates()
            return
        }

        _state.value = _state.value.copy(
            selectedPickupDate = date,
            showDatePicker = false,
            orderError = null  // Clear any previous errors
        )
    }

    // ===== Utility Functions =====

    /**
     * Check if order can be edited based on pickup date
     * Returns true if more than 3 days before pickup
     */
    fun canEditOrder(pickupDate: Long): Boolean {
        val threeDaysBeforePickup = pickupDate - (3 * 24 * 60 * 60 * 1000)
        return Clock.System.now().toEpochMilliseconds() < threeDaysBeforePickup
    }

    /**
     * Format timestamp to readable date string
     */
    fun formatDate(timestamp: Long): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val day = dateTime.dayOfMonth.toString().padStart(2, '0')
        val month = dateTime.monthNumber.toString().padStart(2, '0')
        val year = dateTime.year
        return "$day.$month.$year"
    }

    /**
     * Format timestamp to date key (yyyyMMdd) for Firebase paths
     */
    private fun formatDateKey(timestamp: Long): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val year = dateTime.year
        val month = dateTime.monthNumber.toString().padStart(2, '0')
        val day = dateTime.dayOfMonth.toString().padStart(2, '0')
        return "$year$month$day"
    }

    /**
     * Get days until pickup
     */
    fun getDaysUntilPickup(pickupDate: Long): Long {
        val diff = pickupDate - Clock.System.now().toEpochMilliseconds()
        return diff / (24 * 60 * 60 * 1000)
    }
}
