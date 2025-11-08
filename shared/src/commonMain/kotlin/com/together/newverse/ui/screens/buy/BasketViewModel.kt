package com.together.newverse.ui.screens.buy

import androidx.lifecycle.ViewModel
import com.together.newverse.domain.model.OrderedProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Actions that can be performed on the Basket screen
 */
sealed interface BasketAction {
    data class AddItem(val item: OrderedProduct) : BasketAction
    data class RemoveItem(val productId: String) : BasketAction
    data class UpdateQuantity(val productId: String, val newQuantity: Double) : BasketAction
    data object ClearBasket : BasketAction
    data object Checkout : BasketAction
}

/**
 * State for the Basket screen
 */
data class BasketScreenState(
    val items: List<OrderedProduct> = emptyList(),
    val total: Double = 0.0,
    val isCheckingOut: Boolean = false
)

/**
 * ViewModel for Shopping Basket screen
 */
class BasketViewModel : ViewModel() {

    private val _state = MutableStateFlow(BasketScreenState())
    val state: StateFlow<BasketScreenState> = _state.asStateFlow()

    fun onAction(action: BasketAction) {
        when (action) {
            is BasketAction.AddItem -> addItem(action.item)
            is BasketAction.RemoveItem -> removeItem(action.productId)
            is BasketAction.UpdateQuantity -> updateQuantity(action.productId, action.newQuantity)
            BasketAction.ClearBasket -> clearBasket()
            BasketAction.Checkout -> checkout()
        }
    }

    private fun addItem(item: OrderedProduct) {
        val currentItems = _state.value.items.toMutableList()
        val existingIndex = currentItems.indexOfFirst { it.productId == item.productId }

        if (existingIndex >= 0) {
            // Update quantity if item already exists
            val existing = currentItems[existingIndex]
            currentItems[existingIndex] = existing.copy(
                amountCount = existing.amountCount + item.amountCount
            )
        } else {
            currentItems.add(item)
        }

        _state.value = _state.value.copy(items = currentItems)
        calculateTotal()
    }

    private fun removeItem(productId: String) {
        _state.value = _state.value.copy(
            items = _state.value.items.filter { it.productId != productId }
        )
        calculateTotal()
    }

    private fun updateQuantity(productId: String, newQuantity: Double) {
        val currentItems = _state.value.items.toMutableList()
        val index = currentItems.indexOfFirst { it.productId == productId }

        if (index >= 0) {
            currentItems[index] = currentItems[index].copy(amountCount = newQuantity)
            _state.value = _state.value.copy(items = currentItems)
            calculateTotal()
        }
    }

    private fun clearBasket() {
        _state.value = BasketScreenState()
    }

    private fun calculateTotal() {
        val total = _state.value.items.sumOf { it.price * it.amountCount }
        _state.value = _state.value.copy(total = total)
    }

    private fun checkout() {
        // TODO: Implement checkout flow with OrderRepository
        _state.value = _state.value.copy(isCheckingOut = true)
    }
}
