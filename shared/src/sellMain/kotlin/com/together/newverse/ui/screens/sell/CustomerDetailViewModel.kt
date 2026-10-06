package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.ui.state.core.AsyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CustomerDetailData(
    val buyerId: String,
    val displayName: String,
    val status: AccessStatus,
    val emailAddress: String,
    val telephoneNumber: String,
    val orderCount: Int,
    val totalSpent: Double,
    val lastOrderDate: Long?,
    val orders: List<Order>
)

private val MONEY_COUNTING_STATUSES = setOf(OrderStatus.PLACED, OrderStatus.LOCKED, OrderStatus.COMPLETED)

/**
 * Shows a single buyer's contact info (sourced from their most recent order snapshot,
 * since buyer_profile itself is owner-only and unreadable by the seller) and order
 * history summary. buyerId here is the buyer's auth uid — the same id their orders
 * are keyed by, so no resolution step is needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDetailViewModel(
    private val orderRepository: OrderRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _buyerId = MutableStateFlow<String?>(null)

    val state: StateFlow<AsyncState<CustomerDetailData>> = _buyerId
        .flatMapLatest { buyerId ->
            if (buyerId == null) {
                flowOf(AsyncState.Loading)
            } else {
                loadCustomer(buyerId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AsyncState.Loading
        )

    private fun loadCustomer(buyerId: String) = flow {
        val sellerId = authRepository.getCurrentUserId()
        if (sellerId == null) {
            emit(AsyncState.Error("Not authenticated"))
            return@flow
        }

        // buyer_access_status is the source of truth for status and the fallback name.
        // Its key is the buyer's auth uid, which is also what orders are keyed by, so
        // there is no correlation step and no "requested but never connected" gap.
        val access = profileRepository.observeBuyers(sellerId).first()
            .firstOrNull { it.buyerId == buyerId }
        val fallbackName = access?.displayName.orEmpty()
        val status = access?.status ?: AccessStatus.NONE

        emitAll(
            orderRepository.observeSellerOrders(sellerId).map { orders ->
                val buyerOrders = orders
                    .filter { it.buyerProfile.id == buyerId }
                    .sortedByDescending { it.pickUpDate }
                val moneyOrders = buyerOrders.filter { it.status in MONEY_COUNTING_STATUSES }
                val contact = buyerOrders.firstOrNull()?.buyerProfile

                AsyncState.Success(
                    CustomerDetailData(
                        buyerId = buyerId,
                        displayName = contact?.displayName?.takeIf { it.isNotBlank() } ?: fallbackName,
                        status = status,
                        emailAddress = contact?.emailAddress.orEmpty(),
                        telephoneNumber = contact?.telephoneNumber.orEmpty(),
                        orderCount = buyerOrders.size,
                        totalSpent = moneyOrders.sumOf { order -> order.articles.sumOf { it.getTotalPrice() } },
                        lastOrderDate = buyerOrders.firstOrNull()?.pickUpDate,
                        orders = buyerOrders
                    )
                )
            }
        )
    }.catch { e -> emit(AsyncState.Error(e.message ?: "Failed to load customer")) }

    fun load(buyerId: String) {
        _buyerId.value = buyerId
    }
}
