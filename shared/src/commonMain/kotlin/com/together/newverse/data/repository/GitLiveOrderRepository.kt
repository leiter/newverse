package com.together.newverse.data.repository

import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.isEditable
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
// TODO: Import GitLive SDK classes when ready
// import dev.gitlive.firebase.database.DatabaseReference
// import dev.gitlive.firebase.database.Firebase
// import dev.gitlive.firebase.database.database

/**
 * GitLive implementation of OrderRepository for cross-platform order management.
 *
 * This implementation will use GitLive's Firebase SDK to provide:
 * - Cross-platform support (Android, iOS, Web, Desktop)
 * - Order creation and tracking
 * - Real-time order updates
 * - Order history management
 *
 * Data structure in Firebase:
 * - /orders/{sellerId}/{date}/{orderId} - Order data
 * - /buyer_profiles/{buyerId}/placedOrderIds - Map of date to orderId
 */
class GitLiveOrderRepository(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : OrderRepository {

    // TODO: Initialize GitLive Firebase Database when SDK is ready
    // private val database = Firebase.database
    // private val ordersRootRef = database.reference("orders")

    // Cache for orders
    private val ordersCache = mutableMapOf<String, Order>()
    private val sellerOrdersCache = mutableMapOf<String, List<Order>>()

    /**
     * Observe orders for a seller with real-time updates.
     */
    override fun observeSellerOrders(sellerId: String): Flow<List<Order>> = flow {
        println("🔐 GitLiveOrderRepository.observeSellerOrders: START for sellerId=$sellerId")

        // TODO: Implement real-time listener with GitLive
        // val ordersRef = ordersRootRef.child(sellerId)
        // ordersRef.valueEvents.collect { snapshot ->
        //     val orders = mutableListOf<Order>()
        //     snapshot.children.forEach { dateSnapshot ->
        //         dateSnapshot.children.forEach { orderSnapshot ->
        //             val orderDto = orderSnapshot.value<OrderDto>()
        //             orderDto?.toDomain()?.let { orders.add(it) }
        //         }
        //     }
        //     emit(orders)
        // }

        // Temporary mock implementation
        val mockOrders = createMockSellerOrders(sellerId)
        sellerOrdersCache[sellerId] = mockOrders
        emit(mockOrders)
    }

    /**
     * Get buyer's placed orders.
     */
    override suspend fun getBuyerOrders(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Result<List<Order>> {
        return try {
            println("🔐 GitLiveOrderRepository.getBuyerOrders: START - ${placedOrderIds.size} orders")

            if (placedOrderIds.isEmpty()) {
                return Result.success(emptyList())
            }

            val orders = mutableListOf<Order>()

            // TODO: Implement with GitLive
            // placedOrderIds.forEach { (date, orderId) ->
            //     val snapshot = ordersRootRef.child(sellerId).child(date).child(orderId).get()
            //     val orderDto = snapshot.value<OrderDto>()
            //     orderDto?.toDomain()?.let { orders.add(it) }
            // }

            // For now, return mock orders
            placedOrderIds.forEach { (date, orderId) ->
                val mockOrder = createMockOrder(orderId, sellerId, date)
                orders.add(mockOrder)
                ordersCache[orderId] = mockOrder
            }

            println("✅ GitLiveOrderRepository.getBuyerOrders: Found ${orders.size} orders")
            Result.success(orders)

        } catch (e: Exception) {
            println("❌ GitLiveOrderRepository.getBuyerOrders: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Place a new order.
     */
    override suspend fun placeOrder(order: Order): Result<Order> {
        return try {
            println("🔐 GitLiveOrderRepository.placeOrder: START")

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                return Result.failure(Exception("User not authenticated"))
            }

            // Generate order ID if not present
            val orderId = if (order.id.isEmpty()) {
                "order_${Clock.System.now().toEpochMilliseconds()}"
            } else {
                order.id
            }

            val finalOrder = order.copy(
                id = orderId,
                createdDate = Clock.System.now().toEpochMilliseconds(),
                status = OrderStatus.PLACED
            )

            // TODO: Implement with GitLive
            // val orderPath = "${order.sellerId}/${formatDate(order.pickUpDate)}/$orderId"
            // val dto = OrderDto.fromDomain(finalOrder)
            // ordersRootRef.child(orderPath).setValue(dto)
            //
            // // Update buyer profile with order ID
            // val buyerProfile = profileRepository.getBuyerProfile().getOrThrow()
            // val updatedProfile = buyerProfile.copy(
            //     placedOrderIds = buyerProfile.placedOrderIds + (formatDate(order.pickUpDate) to orderId)
            // )
            // profileRepository.saveBuyerProfile(updatedProfile)

            // Cache the order
            ordersCache[orderId] = finalOrder

            println("✅ GitLiveOrderRepository.placeOrder: Success - orderId=$orderId")
            Result.success(finalOrder)

        } catch (e: Exception) {
            println("❌ GitLiveOrderRepository.placeOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update an existing order.
     */
    override suspend fun updateOrder(order: Order): Result<Unit> {
        return try {
            println("🔐 GitLiveOrderRepository.updateOrder: START - orderId=${order.id}")

            if (!order.status.isEditable()) {
                return Result.failure(Exception("Order is not editable (status: ${order.status})"))
            }

            // TODO: Implement with GitLive
            // val orderPath = "${order.sellerId}/${formatDate(order.pickUpDate)}/${order.id}"
            // val dto = OrderDto.fromDomain(order)
            // ordersRootRef.child(orderPath).setValue(dto)

            // Update cache
            ordersCache[order.id] = order

            println("✅ GitLiveOrderRepository.updateOrder: Success")
            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ GitLiveOrderRepository.updateOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Cancel an order.
     */
    override suspend fun cancelOrder(
        sellerId: String,
        date: String,
        orderId: String
    ): Result<Boolean> {
        return try {
            println("🔐 GitLiveOrderRepository.cancelOrder: START - orderId=$orderId")

            // TODO: Implement with GitLive
            // val orderRef = ordersRootRef.child(sellerId).child(date).child(orderId)
            // val snapshot = orderRef.get()
            // val orderDto = snapshot.value<OrderDto>()
            //
            // if (orderDto != null) {
            //     val cancelledOrder = orderDto.toDomain().copy(status = OrderStatus.CANCELLED)
            //     orderRef.setValue(OrderDto.fromDomain(cancelledOrder))
            // }

            // Update cache
            ordersCache[orderId]?.let {
                ordersCache[orderId] = it.copy(status = OrderStatus.CANCELLED)
            }

            println("✅ GitLiveOrderRepository.cancelOrder: Success")
            Result.success(true)

        } catch (e: Exception) {
            println("❌ GitLiveOrderRepository.cancelOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Load a specific order.
     */
    override suspend fun loadOrder(
        sellerId: String,
        orderId: String,
        orderPath: String
    ): Result<Order> {
        return try {
            println("🔐 GitLiveOrderRepository.loadOrder: START - orderId=$orderId")

            // Check cache first
            ordersCache[orderId]?.let {
                println("✅ GitLiveOrderRepository.loadOrder: Found in cache")
                return Result.success(it)
            }

            // TODO: Implement with GitLive
            // val snapshot = database.reference(orderPath).get()
            // val orderDto = snapshot.value<OrderDto>()
            // val order = orderDto?.toDomain()

            // For now, return mock order
            val mockOrder = createMockOrder(orderId, sellerId, "")
            ordersCache[orderId] = mockOrder

            println("✅ GitLiveOrderRepository.loadOrder: Created mock order")
            Result.success(mockOrder)

        } catch (e: Exception) {
            println("❌ GitLiveOrderRepository.loadOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get the most recent open/editable order for the current buyer.
     */
    override suspend fun getOpenEditableOrder(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Result<Order?> {
        return try {
            println("🔐 GitLiveOrderRepository.getOpenEditableOrder: START")

            if (placedOrderIds.isEmpty()) {
                println("✅ GitLiveOrderRepository.getOpenEditableOrder: No placed orders")
                return Result.success(null)
            }

            // Get all buyer orders
            val ordersResult = getBuyerOrders(sellerId, placedOrderIds)
            if (ordersResult.isFailure) {
                return Result.failure(ordersResult.exceptionOrNull()!!)
            }

            val orders = ordersResult.getOrThrow()

            // Find most recent editable order
            val editableOrder = orders
                .filter { it.canEdit() }
                .maxByOrNull { it.createdDate }

            if (editableOrder != null) {
                println("✅ GitLiveOrderRepository.getOpenEditableOrder: Found editable order ${editableOrder.id}")
            } else {
                println("✅ GitLiveOrderRepository.getOpenEditableOrder: No editable orders found")
            }

            Result.success(editableOrder)

        } catch (e: Exception) {
            println("❌ GitLiveOrderRepository.getOpenEditableOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    // Helper functions

    /**
     * Create mock orders for testing seller view.
     */
    private fun createMockSellerOrders(sellerId: String): List<Order> {
        val now = Clock.System.now().toEpochMilliseconds()
        return listOf(
            Order(
                id = "order_001",
                buyerProfile = BuyerProfile(
                    id = "buyer_001",
                    displayName = "John Doe (GitLive)",
                    emailAddress = "john@example.com"
                ),
                createdDate = now - 86400000, // Yesterday
                sellerId = sellerId,
                marketId = "market_001",
                pickUpDate = now + 259200000, // 3 days from now
                message = "Please pack carefully",
                articles = listOf(
                    OrderedProduct(
                        id = "op_001",
                        productId = "PROD001",
                        productName = "Fresh Apples",
                        unit = "kg",
                        price = 2.99,
                        amount = "2 kg",
                        amountCount = 2.0
                    )
                ),
                status = OrderStatus.PLACED
            ),
            Order(
                id = "order_002",
                buyerProfile = BuyerProfile(
                    id = "buyer_002",
                    displayName = "Jane Smith (GitLive)",
                    emailAddress = "jane@example.com"
                ),
                createdDate = now - 172800000, // 2 days ago
                sellerId = sellerId,
                marketId = "market_001",
                pickUpDate = now + 86400000, // Tomorrow
                message = "",
                articles = listOf(
                    OrderedProduct(
                        id = "op_002",
                        productId = "PROD002",
                        productName = "Organic Bananas",
                        unit = "kg",
                        price = 1.99,
                        amount = "1.5 kg",
                        amountCount = 1.5
                    ),
                    OrderedProduct(
                        id = "op_003",
                        productId = "PROD003",
                        productName = "Farm Eggs",
                        unit = "dozen",
                        price = 4.50,
                        amount = "2 dozen",
                        amountCount = 2.0
                    )
                ),
                status = OrderStatus.LOCKED
            )
        )
    }

    /**
     * Create a single mock order.
     */
    private fun createMockOrder(orderId: String, sellerId: String, date: String): Order {
        val now = Clock.System.now().toEpochMilliseconds()
        return Order(
            id = orderId,
            buyerProfile = BuyerProfile(
                id = "current_user",
                displayName = "Test User (GitLive)",
                emailAddress = "test@example.com"
            ),
            createdDate = now - 3600000, // 1 hour ago
            sellerId = sellerId,
            marketId = "market_001",
            pickUpDate = now + 604800000, // 1 week from now
            message = "Mock order for testing",
            articles = listOf(
                OrderedProduct(
                    id = "mock_op_001",
                    productId = "MOCK_PROD",
                    productName = "Mock Product",
                    unit = "piece",
                    price = 9.99,
                    amount = "1 piece",
                    amountCount = 1.0
                )
            ),
            status = OrderStatus.PLACED
        )
    }
}

/**
 * Data Transfer Object for Order.
 */
private data class OrderDto(
    val id: String = "",
    val buyerId: String = "",
    val buyerName: String = "",
    val buyerEmail: String = "",
    val createdDate: Long = 0L,
    val sellerId: String = "",
    val marketId: String = "",
    val pickUpDate: Long = 0L,
    val message: String = "",
    val notFavourite: Boolean = true,
    val articles: List<OrderedProductDto> = emptyList(),
    val status: String = OrderStatus.DRAFT.name
) {
    fun toDomain(): Order {
        return Order(
            id = id,
            buyerProfile = BuyerProfile(
                id = buyerId,
                displayName = buyerName,
                emailAddress = buyerEmail
            ),
            createdDate = createdDate,
            sellerId = sellerId,
            marketId = marketId,
            pickUpDate = pickUpDate,
            message = message,
            notFavourite = notFavourite,
            articles = articles.map { it.toDomain() },
            status = OrderStatus.valueOf(status)
        )
    }

    companion object {
        fun fromDomain(order: Order): OrderDto {
            return OrderDto(
                id = order.id,
                buyerId = order.buyerProfile.id,
                buyerName = order.buyerProfile.displayName,
                buyerEmail = order.buyerProfile.emailAddress,
                createdDate = order.createdDate,
                sellerId = order.sellerId,
                marketId = order.marketId,
                pickUpDate = order.pickUpDate,
                message = order.message,
                notFavourite = order.notFavourite,
                articles = order.articles.map { OrderedProductDto.fromDomain(it) },
                status = order.status.name
            )
        }
    }
}

/**
 * Data Transfer Object for OrderedProduct.
 */
private data class OrderedProductDto(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val unit: String = "",
    val price: Double = 0.0,
    val amount: String = "",
    val amountCount: Double = 0.0,
    val piecesCount: Int = -1
) {
    fun toDomain(): OrderedProduct {
        return OrderedProduct(
            id = id,
            productId = productId,
            productName = productName,
            unit = unit,
            price = price,
            amount = amount,
            amountCount = amountCount,
            piecesCount = piecesCount
        )
    }

    companion object {
        fun fromDomain(product: OrderedProduct): OrderedProductDto {
            return OrderedProductDto(
                id = product.id,
                productId = product.productId,
                productName = product.productName,
                unit = product.unit,
                price = product.price,
                amount = product.amount,
                amountCount = product.amountCount,
                piecesCount = product.piecesCount
            )
        }
    }
}