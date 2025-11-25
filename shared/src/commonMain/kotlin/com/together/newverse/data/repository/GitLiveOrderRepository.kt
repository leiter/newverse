package com.together.newverse.data.repository

import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.isEditable
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.DataSnapshot
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

/**
 * GitLive implementation of OrderRepository for cross-platform order management.
 * This version uses the correct GitLive Firebase SDK APIs.
 */
class GitLiveOrderRepository(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : OrderRepository {

    // GitLive Firebase Database references
    private val database = Firebase.database
    private val ordersRootRef = database.reference("orders")
    private val buyerProfilesRef = database.reference("buyer_profile")

    // Cache for orders
    private val ordersCache = mutableMapOf<String, Order>()
    private val sellerOrdersCache = mutableMapOf<String, List<Order>>()

    /**
     * Observe orders for a seller with real-time updates.
     */
    override fun observeSellerOrders(sellerId: String): Flow<List<Order>> = flow {
        println("🔐 GitLiveOrderRepository.observeSellerOrders: START for sellerId=$sellerId")

        try {
            // Create reference to seller's orders
            val ordersRef = ordersRootRef.child(sellerId)

            // Listen for value changes
            ordersRef.valueEvents.collect { snapshot ->
                val orders = mutableListOf<Order>()

                // Process date snapshots
                snapshot.children.forEach { dateSnapshot ->
                    // Process order snapshots within each date
                    dateSnapshot.children.forEach { orderSnapshot ->
                        val order = mapSnapshotToOrder(orderSnapshot)
                        if (order != null && !order.hiddenBySeller) {
                            orders.add(order)
                        }
                    }
                }

                // Update cache and emit
                sellerOrdersCache[sellerId] = orders
                println("🔐 GitLiveOrderRepository.observeSellerOrders: Emitting ${orders.size} orders")
                emit(orders)
            }
        } catch (e: Exception) {
            println("❌ GitLiveOrderRepository.observeSellerOrders: Error - ${e.message}")
            // Emit empty list on error
            emit(emptyList())
        }
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

            // Fetch each order from GitLive Firebase
            placedOrderIds.forEach { (date, orderId) ->
                try {
                    val orderRef = ordersRootRef.child(sellerId).child(date).child(orderId)
                    val snapshot = orderRef.valueEvents.first()

                    if (snapshot.exists) {
                        val order = mapSnapshotToOrder(snapshot)
                        if (order != null) {
                            orders.add(order)
                            ordersCache[orderId] = order
                        }
                    }
                } catch (e: Exception) {
                    println("⚠️ GitLiveOrderRepository.getBuyerOrders: Failed to fetch order $orderId: ${e.message}")
                }
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

            // Format date for Firebase path
            val dateString = formatDate(finalOrder.pickUpDate)

            // Convert to map for Firebase
            val orderMap = orderToMap(finalOrder)

            // Save to GitLive Firebase
            val orderRef = ordersRootRef.child(order.sellerId).child(dateString).child(orderId)
            orderRef.setValue(orderMap)

            // Update buyer profile with order ID
            val buyerProfile = profileRepository.getBuyerProfile().getOrThrow()
            val updatedProfile = buyerProfile.copy(
                placedOrderIds = buyerProfile.placedOrderIds + (dateString to orderId)
            )
            profileRepository.saveBuyerProfile(updatedProfile)

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

            // Format date for Firebase path
            val dateString = formatDate(order.pickUpDate)

            // Convert to map for Firebase
            val orderMap = orderToMap(order)

            // Save to GitLive Firebase
            val orderRef = ordersRootRef.child(order.sellerId).child(dateString).child(order.id)
            orderRef.setValue(orderMap)

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

            // Fetch the order from GitLive Firebase
            val orderRef = ordersRootRef.child(sellerId).child(date).child(orderId)
            val snapshot = orderRef.valueEvents.first()

            if (snapshot.exists) {
                val order = mapSnapshotToOrder(snapshot)
                if (order != null) {
                    // Update status to cancelled
                    val cancelledOrder = order.copy(status = OrderStatus.CANCELLED)
                    val orderMap = orderToMap(cancelledOrder)

                    // Save back to Firebase
                    orderRef.setValue(orderMap)

                    // Update cache
                    ordersCache[orderId] = cancelledOrder

                    println("✅ GitLiveOrderRepository.cancelOrder: Success")
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to parse order data"))
                }
            } else {
                println("❌ GitLiveOrderRepository.cancelOrder: Order not found")
                Result.failure(Exception("Order not found"))
            }

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

            // Fetch from GitLive Firebase
            val orderRef = database.reference(orderPath)
            val snapshot = orderRef.valueEvents.first()

            if (snapshot.exists) {
                val order = mapSnapshotToOrder(snapshot)
                if (order != null) {
                    // Update cache
                    ordersCache[orderId] = order

                    println("✅ GitLiveOrderRepository.loadOrder: Fetched from Firebase")
                    Result.success(order)
                } else {
                    Result.failure(Exception("Failed to parse order data"))
                }
            } else {
                println("❌ GitLiveOrderRepository.loadOrder: Order not found")
                Result.failure(Exception("Order not found"))
            }

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
     * Format a timestamp to a date string for Firebase paths.
     */
    private fun formatDate(timestamp: Long): String {
        // Convert timestamp to YYYY-MM-DD format
        val date = kotlinx.datetime.Instant.fromEpochMilliseconds(timestamp)
        val timezone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val localDateTime = date.toLocalDateTime(timezone)
        return "${localDateTime.year}-${localDateTime.monthNumber.toString().padStart(2, '0')}-${localDateTime.dayOfMonth.toString().padStart(2, '0')}"
    }

    /**
     * Map a DataSnapshot to an Order domain model.
     */
    private fun mapSnapshotToOrder(snapshot: DataSnapshot): Order? {
        val orderId = snapshot.key ?: return null
        val value = snapshot.value

        return when (value) {
            is Map<*, *> -> {
                try {
                    // Map articles
                    val articlesData = value["articles"] as? List<*> ?: emptyList<Any>()
                    val articles = articlesData.mapNotNull { articleData ->
                        when (articleData) {
                            is Map<*, *> -> OrderedProduct(
                                id = articleData["id"] as? String ?: "",
                                productId = articleData["productId"] as? String ?: "",
                                productName = articleData["productName"] as? String ?: "",
                                unit = articleData["unit"] as? String ?: "",
                                price = (articleData["price"] as? Number)?.toDouble() ?: 0.0,
                                amount = articleData["amount"] as? String ?: "",
                                amountCount = (articleData["amountCount"] as? Number)?.toDouble() ?: 0.0,
                                piecesCount = (articleData["piecesCount"] as? Number)?.toInt() ?: -1
                            )
                            else -> null
                        }
                    }

                    Order(
                        id = orderId,
                        buyerProfile = BuyerProfile(
                            id = value["buyerId"] as? String ?: "",
                            displayName = value["buyerName"] as? String ?: "",
                            emailAddress = value["buyerEmail"] as? String ?: ""
                        ),
                        createdDate = (value["createdDate"] as? Number)?.toLong() ?: 0L,
                        sellerId = value["sellerId"] as? String ?: "",
                        marketId = value["marketId"] as? String ?: "",
                        pickUpDate = (value["pickUpDate"] as? Number)?.toLong() ?: 0L,
                        message = value["message"] as? String ?: "",
                        notFavourite = value["notFavourite"] as? Boolean ?: true,
                        articles = articles,
                        status = try {
                            OrderStatus.valueOf(value["status"] as? String ?: "DRAFT")
                        } catch (e: Exception) {
                            OrderStatus.DRAFT
                        },
                        hiddenBySeller = value["hiddenBySeller"] as? Boolean ?: false,
                        hiddenByBuyer = value["hiddenByBuyer"] as? Boolean ?: false
                    )
                } catch (e: Exception) {
                    println("❌ Error mapping order snapshot: ${e.message}")
                    null
                }
            }
            else -> null
        }
    }

    /**
     * Convert an Order to a map for Firebase storage.
     */
    private fun orderToMap(order: Order): Map<String, Any?> {
        return mapOf(
            "id" to order.id,
            "buyerId" to order.buyerProfile.id,
            "buyerName" to order.buyerProfile.displayName,
            "buyerEmail" to order.buyerProfile.emailAddress,
            "createdDate" to order.createdDate,
            "sellerId" to order.sellerId,
            "marketId" to order.marketId,
            "pickUpDate" to order.pickUpDate,
            "message" to order.message,
            "notFavourite" to order.notFavourite,
            "articles" to order.articles.map { article ->
                mapOf(
                    "id" to article.id,
                    "productId" to article.productId,
                    "productName" to article.productName,
                    "unit" to article.unit,
                    "price" to article.price,
                    "amount" to article.amount,
                    "amountCount" to article.amountCount,
                    "piecesCount" to article.piecesCount
                )
            },
            "status" to order.status.name,
            "hiddenBySeller" to order.hiddenBySeller,
            "hiddenByBuyer" to order.hiddenByBuyer
        )
    }

    override suspend fun hideOrderForSeller(sellerId: String, date: String, orderId: String): Result<Boolean> {
        return try {
            println("🔐 GitLiveOrderRepository.hideOrderForSeller: START - orderId=$orderId")

            val orderRef = ordersRootRef.child(sellerId).child(date).child(orderId).child("hiddenBySeller")
            orderRef.setValue(true)

            println("✅ GitLiveOrderRepository.hideOrderForSeller: Success")
            Result.success(true)

        } catch (e: Exception) {
            println("❌ GitLiveOrderRepository.hideOrderForSeller: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun hideOrderForBuyer(sellerId: String, date: String, orderId: String): Result<Boolean> {
        return try {
            println("🔐 GitLiveOrderRepository.hideOrderForBuyer: START - orderId=$orderId")

            val orderRef = ordersRootRef.child(sellerId).child(date).child(orderId).child("hiddenByBuyer")
            orderRef.setValue(true)

            println("✅ GitLiveOrderRepository.hideOrderForBuyer: Success")
            Result.success(true)

        } catch (e: Exception) {
            println("❌ GitLiveOrderRepository.hideOrderForBuyer: Error - ${e.message}")
            Result.failure(e)
        }
    }

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