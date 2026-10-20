package com.together.newverse.data.repository

import com.together.newverse.domain.model.DraftBasket
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.repository.BasketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import com.together.newverse.util.Log

/**
 * In-memory implementation of BasketRepository
 * Stores basket items in memory during app session
 */
class InMemoryBasketRepository : BasketRepository {

    private companion object {
        private const val TAG = "BasketRepo"
    }

    private val _basket = MutableStateFlow<List<OrderedProduct>>(emptyList())
    private var _loadedOrderId: String? = null
    private var _loadedOrderDate: String? = null

    override fun observeBasket(): StateFlow<List<OrderedProduct>> {
        return _basket.asStateFlow()
    }

    override suspend fun addItem(item: OrderedProduct) {
        val currentItems = _basket.value.toMutableList()
        // Match by id (Firebase key when loaded from order) or productId (when freshly added)
        val existingIndex = currentItems.indexOfFirst {
            (item.id.isNotEmpty() && it.id == item.id) || it.productId == item.productId
        }

        if (existingIndex >= 0) {
            // Update quantity if item already exists.
            // `amount` is cleared rather than rewritten: it is the display string, and
            // OrderedProduct formats it from amountCount with the unit attached. Writing
            // a raw Double here is what used to show the seller "2.0" with no unit.
            val existing = currentItems[existingIndex]
            currentItems[existingIndex] = existing.copy(
                amountCount = existing.amountCount + item.amountCount,
                amount = ""
            )
        } else {
            currentItems.add(item)
        }

        _basket.value = currentItems
        Log.d(TAG) { "BasketRepository.addItem: Added ${item.productName}, basket now has ${currentItems.size} items" }
    }

    override suspend fun removeItem(productId: String) {
        _basket.value = _basket.value.filter { it.id != productId && it.productId != productId }
        Log.d(TAG) { "BasketRepository.removeItem: Removed product $productId, basket now has ${_basket.value.size} items" }
    }

    override suspend fun updateQuantity(productId: String, newQuantity: Double) {
        val currentItems = _basket.value.toMutableList()
        val index = currentItems.indexOfFirst { it.id == productId || it.productId == productId }

        if (index >= 0) {
            val item = currentItems[index]
            // weightPerPiece is not available here, so the existing pieces-per-unit
            // ratio is carried over. A piece count of -1 means "not applicable" (a
            // weight-priced item, or an order that stored none): scaling it would turn
            // it into a negative count and write that to Firebase, so it stays as it is.
            val newPiecesCount = when {
                item.piecesCount < 0 -> item.piecesCount
                item.amountCount > 0 -> ((item.piecesCount / item.amountCount) * newQuantity).toInt()
                else -> newQuantity.toInt()
            }

            currentItems[index] = item.copy(
                amountCount = newQuantity,
                amount = "",
                piecesCount = newPiecesCount
            )
            _basket.value = currentItems
            Log.d(TAG) { "BasketRepository.updateQuantity: Updated product $productId to quantity $newQuantity (pieces: $newPiecesCount)" }
        }
    }

    override suspend fun clearBasket() {
        _basket.value = emptyList()
        _loadedOrderId = null
        _loadedOrderDate = null
        Log.d(TAG) { "BasketRepository.clearBasket: Basket cleared" }
    }

    override fun getTotal(): Double {
        return _basket.value.sumOf { it.price * it.amountCount }
    }

    override fun getItemCount(): Int {
        return _basket.value.size
    }

    override suspend fun loadOrderItems(items: List<OrderedProduct>, orderId: String, orderDate: String) {
        _basket.value = items
        _loadedOrderId = orderId
        _loadedOrderDate = orderDate
        Log.d(TAG) { "BasketRepository.loadOrderItems: Loaded ${items.size} items from order $orderId (date: $orderDate)" }
    }

    override fun getLoadedOrderInfo(): Pair<String, String>? {
        return if (_loadedOrderId != null && _loadedOrderDate != null) {
            Pair(_loadedOrderId!!, _loadedOrderDate!!)
        } else {
            null
        }
    }

    override fun hasDraftBasket(): Boolean {
        // Has draft if basket has items AND it's not loaded from an existing order
        return _basket.value.isNotEmpty() && _loadedOrderId == null
    }

    override suspend fun loadFromProfile(draftBasket: DraftBasket) {
        _basket.value = draftBasket.items
        // Draft baskets are not tied to any order
        _loadedOrderId = null
        _loadedOrderDate = null
        Log.d(TAG) { "BasketRepository.loadFromProfile: Loaded ${draftBasket.items.size} items from draft basket" }
    }

    override fun toDraftBasket(selectedPickupDate: String?, selectedMarketId: String): DraftBasket {
        return DraftBasket(
            items = _basket.value,
            selectedPickupDate = selectedPickupDate,
            selectedMarketId = selectedMarketId,
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
    }
}
