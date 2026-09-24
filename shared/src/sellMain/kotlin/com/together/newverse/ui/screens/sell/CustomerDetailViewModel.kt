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
 * history summary. buyerId here is the buyerUUID shown in the seller's access list,
 * which is resolved to the auth uid orders are keyed by via buyer_access_status.
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

        // The access list (approvedBuyerIds/blockedClientIds) is the source of truth for
        // status and the fallback name - the same maps SellerProfileScreen shows.
        val sellerProfile = profileRepository.getSellerProfile(sellerId).getOrNull()
        val (fallbackName, status) = when {
            sellerProfile == null -> "" to AccessStatus.NONE
            buyerId in sellerProfile.approvedBuyerIds -> sellerProfile.approvedBuyerIds[buyerId].orEmpty() to AccessStatus.APPROVED
            buyerId in sellerProfile.blockedClientIds -> sellerProfile.blockedClientIds[buyerId].orEmpty() to AccessStatus.BLOCKED
            else -> "" to AccessStatus.NONE
        }

        val authUID = profileRepository.getBuyerAuthUID(sellerId, buyerId)
        if (authUID.isBlank()) {
            // Buyer requested access but has never connected - no orders to correlate yet.
            emit(
                AsyncState.Success(
                    CustomerDetailData(
                        buyerId = buyerId,
                        displayName = fallbackName,
                        status = status,
                        emailAddress = "",
                        telephoneNumber = "",
                        orderCount = 0,
                        totalSpent = 0.0,
                        lastOrderDate = null,
                        orders = emptyList()
                    )
                )
            )
            return@flow
        }

        emitAll(
            orderRepository.observeSellerOrders(sellerId).map { orders ->
                val buyerOrders = orders
                    .filter { it.buyerProfile.id == authUID }
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
