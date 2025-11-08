package com.together.newverse.ui.screens.buy

import androidx.lifecycle.ViewModel
import com.together.newverse.domain.model.OrderedProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for Shopping Basket screen
 */
class BasketViewModel : ViewModel() {

    private val _basketItems = MutableStateFlow<List<OrderedProduct>>(emptyList())
    val basketItems: StateFlow<List<OrderedProduct>> = _basketItems.asStateFlow()

    private val _totalAmount = MutableStateFlow(0.0)
    val totalAmount: StateFlow<Double> = _totalAmount.asStateFlow()

    fun addItem(item: OrderedProduct) {
        val currentItems = _basketItems.value.toMutableList()
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

        _basketItems.value = currentItems
        calculateTotal()
    }

    fun removeItem(productId: String) {
        _basketItems.value = _basketItems.value.filter { it.productId != productId }
        calculateTotal()
    }

    fun updateQuantity(productId: String, newQuantity: Double) {
        val currentItems = _basketItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.productId == productId }

        if (index >= 0) {
            currentItems[index] = currentItems[index].copy(amountCount = newQuantity)
            _basketItems.value = currentItems
            calculateTotal()
        }
    }

    fun clearBasket() {
        _basketItems.value = emptyList()
        _totalAmount.value = 0.0
    }

    private fun calculateTotal() {
        _totalAmount.value = _basketItems.value.sumOf { it.price * it.amountCount }
    }

    fun checkout() {
        // TODO: Implement checkout flow with OrderRepository
    }
}
