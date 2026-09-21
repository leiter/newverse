package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.ProductPricing
import com.together.newverse.domain.model.Sale
import com.together.newverse.domain.model.toSale
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.SaleRepository
import com.together.newverse.domain.repository.SellerArticleRepository
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The pickup of one order: confirming what was handed over, which books a [Sale],
 * marking that nobody came, and cancelling a booking made by mistake.
 *
 * Whether an order is booked comes from the seller's sale records, never from the
 * order: the buyer can edit their order, but not the books.
 */
class PickupViewModel(
    private val saleRepository: SaleRepository,
    private val sellerArticleRepository: SellerArticleRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {

    private val _state = MutableStateFlow(PickupUiState())
    val state: StateFlow<PickupUiState> = _state.asStateFlow()

    private var order: Order? = null

    /** Show the pickup state of [order]. Called again whenever the order changes. */
    fun load(order: Order) {
        this.order = order
        if (order.isDemoOrder || order.status in NOT_BOOKABLE) {
            _state.value = PickupUiState(status = PickupStatus.Unavailable)
            return
        }
        viewModelScope.launch { refreshStatus(order) }
    }

    private suspend fun refreshStatus(order: Order) {
        val sellerId = authRepository.getCurrentUserId() ?: return
        saleRepository.salesForOrder(sellerId, order.id)
            .onSuccess { sales ->
                val booked = activeSale(sales)
                _state.update {
                    it.copy(
                        status = when {
                            booked != null -> PickupStatus.Booked(booked)
                            order.status == OrderStatus.NOT_PICKED_UP -> PickupStatus.NotPickedUp
                            else -> PickupStatus.Open
                        }
                    )
                }
            }
            .onFailure {
                // Unknown whether it is booked: offering to confirm could book it twice.
                _state.update { it.copy(status = PickupStatus.Unknown) }
            }
    }

    // ----- Confirming -----

    /** Open the editor, prefilled with the ordered quantities. */
    fun startConfirm() {
        val order = order ?: return
        if (_state.value.status != PickupStatus.Open) return
        _state.update {
            it.copy(
                isEditing = true,
                lines = order.articles.map { item ->
                    PickupLine(
                        name = item.productName,
                        unit = item.unit,
                        orderedQuantity = item.amountCount,
                        input = formatQuantity(item.amountCount)
                    )
                }
            )
        }
    }

    fun setQuantity(index: Int, input: String) {
        _state.update { state ->
            state.copy(lines = state.lines.mapIndexed { i, line -> if (i == index) line.copy(input = input) else line })
        }
    }

    /** The item was not there: hand over nothing of it. */
    fun setMissing(index: Int) = setQuantity(index, "0")

    fun dismissEditor() {
        _state.update { it.copy(isEditing = false, lines = emptyList()) }
    }

    /** Book what was handed over and mark the order completed. */
    fun confirm() {
        val order = order ?: return
        val quantities = _state.value.lines.map { ProductPricing.parseDecimal(it.input) }
        if (quantities.any { it == null || it < 0.0 }) {
            _state.update { it.copy(message = PickupMessage.INVALID_QUANTITY) }
            return
        }
        val handedOver = quantities.map { it!! }
        if (handedOver.all { it == 0.0 }) {
            _state.update { it.copy(message = PickupMessage.NOTHING_HANDED_OVER) }
            return
        }

        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            _state.update { it.copy(isSaving = true) }

            // Check again right before booking: another device may have confirmed it.
            val existing = saleRepository.salesForOrder(sellerId, order.id).getOrElse {
                failWith(PickupMessage.SAVE_FAILED); return@launch
            }
            activeSale(existing)?.let { booked ->
                _state.update {
                    it.copy(isSaving = false, isEditing = false, status = PickupStatus.Booked(booked),
                        message = PickupMessage.ALREADY_BOOKED)
                }
                return@launch
            }

            val catalog = try {
                sellerArticleRepository.observeSellerArticles(sellerId).first().associateBy { it.id }
            } catch (e: Exception) {
                failWith(PickupMessage.SAVE_FAILED); return@launch
            }
            val sale = order.toSale(handedOver, catalog, confirmedAt = now())

            saleRepository.recordSale(sellerId, sale)
                .onSuccess { stored ->
                    _state.update {
                        it.copy(isSaving = false, isEditing = false, lines = emptyList(),
                            status = PickupStatus.Booked(stored))
                    }
                    // The sale is booked either way; the status only tells the buyer.
                    setOrderStatus(sellerId, order, OrderStatus.COMPLETED)
                }
                .onFailure { failWith(PickupMessage.SAVE_FAILED) }
        }
    }

    // ----- Not picked up -----

    fun markNotPickedUp() {
        val order = order ?: return
        if (_state.value.status != PickupStatus.Open) return
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            if (setOrderStatus(sellerId, order, OrderStatus.NOT_PICKED_UP)) {
                _state.update { it.copy(status = PickupStatus.NotPickedUp, isEditing = false) }
            }
        }
    }

    fun undoNotPickedUp() {
        val order = order ?: return
        if (_state.value.status != PickupStatus.NotPickedUp) return
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            if (setOrderStatus(sellerId, order, OrderStatus.LOCKED)) {
                _state.update { it.copy(status = PickupStatus.Open) }
            }
        }
    }

    // ----- Cancelling a booking -----

    /** Book the cancellation of the current sale; the order can then be confirmed anew. */
    fun cancelBooking() {
        val order = order ?: return
        val booked = (_state.value.status as? PickupStatus.Booked)?.sale ?: return
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            _state.update { it.copy(isSaving = true) }
            saleRepository.recordSale(sellerId, booked.reversal(confirmedAt = now()))
                .onSuccess {
                    _state.update { it.copy(isSaving = false, status = PickupStatus.Open) }
                    setOrderStatus(sellerId, order, OrderStatus.LOCKED)
                }
                .onFailure { failWith(PickupMessage.SAVE_FAILED) }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    // ----- Helpers -----

    private suspend fun setOrderStatus(sellerId: String, order: Order, status: OrderStatus): Boolean {
        val dateKey = OrderDateUtils.formatDateKey(Instant.fromEpochMilliseconds(order.pickUpDate))
        val result = orderRepository.updateOrderStatus(sellerId, dateKey, order.id, status, isDemo = false)
        if (result.isFailure) _state.update { it.copy(message = PickupMessage.STATUS_UPDATE_FAILED) }
        return result.isSuccess
    }

    private fun failWith(message: PickupMessage) {
        _state.update { it.copy(isSaving = false, message = message) }
    }

    private fun formatQuantity(quantity: Double): String =
        if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString() else quantity.toString()

    companion object {
        private val NOT_BOOKABLE = setOf(OrderStatus.DRAFT, OrderStatus.CANCELLED, OrderStatus.DEMO_ORDER)

        /** The sale currently standing for an order: the latest one not cancelled. */
        fun activeSale(sales: List<Sale>): Sale? {
            val cancelled = sales.mapNotNull { it.reverses }.toSet()
            return sales.filter { !it.isReversal && it.id !in cancelled }.maxByOrNull { it.confirmedAt }
        }
    }
}

data class PickupUiState(
    val status: PickupStatus = PickupStatus.Loading,
    /** The editor's lines while confirming; empty otherwise. */
    val lines: List<PickupLine> = emptyList(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val message: PickupMessage? = null
)

sealed interface PickupStatus {
    data object Loading : PickupStatus
    /** Draft, cancelled or demo orders: nothing to book. */
    data object Unavailable : PickupStatus
    /** The sale records could not be read; confirming is not offered. */
    data object Unknown : PickupStatus
    data object Open : PickupStatus
    data class Booked(val sale: Sale) : PickupStatus
    data object NotPickedUp : PickupStatus
}

data class PickupLine(
    val name: String,
    val unit: String,
    val orderedQuantity: Double,
    /** What the seller typed: the handed-over quantity, comma or dot. */
    val input: String
)

enum class PickupMessage {
    INVALID_QUANTITY,
    NOTHING_HANDED_OVER,
    ALREADY_BOOKED,
    SAVE_FAILED,
    STATUS_UPDATE_FAILED
}
