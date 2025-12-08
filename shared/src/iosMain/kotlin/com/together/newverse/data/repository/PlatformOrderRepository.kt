package com.together.newverse.data.repository

import com.together.newverse.data.config.FeatureFlags
import com.together.newverse.data.config.AuthProvider
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow

/**
 * iOS-specific implementation of OrderRepository.
 *
 * Currently iOS uses GitLive exclusively for cross-platform Firebase support.
 */
class PlatformOrderRepository(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : OrderRepository {

    private val actualRepository: OrderRepository by lazy {
        when (FeatureFlags.authProvider) {
            AuthProvider.FIREBASE -> {
                // iOS doesn't have native Firebase wrapper yet, fall back to GitLive
                println("🏭 PlatformOrderRepository (iOS): Firebase requested but using GitLive")
                GitLiveOrderRepository(authRepository, profileRepository)
            }
            AuthProvider.GITLIVE -> {
                println("🏭 PlatformOrderRepository (iOS): Using GitLive (cross-platform)")
                GitLiveOrderRepository(authRepository, profileRepository)
            }
            AuthProvider.AUTO -> {
                // iOS defaults to GitLive
                println("🏭 PlatformOrderRepository (iOS): Using GitLive (iOS default)")
                GitLiveOrderRepository(authRepository, profileRepository)
            }
        }
    }

    override fun observeSellerOrders(sellerId: String): Flow<List<Order>> {
        return actualRepository.observeSellerOrders(sellerId)
    }

    override suspend fun getBuyerOrders(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Result<List<Order>> {
        return actualRepository.getBuyerOrders(sellerId, placedOrderIds)
    }

    override suspend fun placeOrder(order: Order): Result<Order> {
        return actualRepository.placeOrder(order)
    }

    override suspend fun updateOrder(order: Order): Result<Unit> {
        return actualRepository.updateOrder(order)
    }

    override suspend fun cancelOrder(
        sellerId: String,
        date: String,
        orderId: String
    ): Result<Boolean> {
        return actualRepository.cancelOrder(sellerId, date, orderId)
    }

    override suspend fun loadOrder(
        sellerId: String,
        orderId: String,
        orderPath: String
    ): Result<Order> {
        return actualRepository.loadOrder(sellerId, orderId, orderPath)
    }

    override suspend fun getOpenEditableOrder(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Result<Order?> {
        return actualRepository.getOpenEditableOrder(sellerId, placedOrderIds)
    }

    override fun observeBuyerOrders(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Flow<List<Order>> {
        return actualRepository.observeBuyerOrders(sellerId, placedOrderIds)
    }

    override suspend fun hideOrderForSeller(
        sellerId: String,
        date: String,
        orderId: String
    ): Result<Boolean> {
        return actualRepository.hideOrderForSeller(sellerId, date, orderId)
    }

    override suspend fun hideOrderForBuyer(
        sellerId: String,
        date: String,
        orderId: String
    ): Result<Boolean> {
        return actualRepository.hideOrderForBuyer(sellerId, date, orderId)
    }

    override suspend fun getUpcomingOrder(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Result<Order?> {
        return actualRepository.getUpcomingOrder(sellerId, placedOrderIds)
    }
}
