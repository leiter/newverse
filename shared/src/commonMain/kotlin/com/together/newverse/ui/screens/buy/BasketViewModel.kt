package com.together.newverse.ui.screens.buy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.repository.BasketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
class BasketViewModel(
    private val basketRepository: BasketRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BasketScreenState())
    val state: StateFlow<BasketScreenState> = _state.asStateFlow()

    init {
        // Observe basket changes from repository
        viewModelScope.launch {
            basketRepository.observeBasket().collect { items ->
                _state.value = _state.value.copy(
                    items = items,
                    total = basketRepository.getTotal()
                )
            }
        }
    }

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
        // TODO: Implement checkout flow with OrderRepository
        _state.value = _state.value.copy(isCheckingOut = true)
    }
}
