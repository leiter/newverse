package com.together.newverse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.together.newverse.data.firebase.Database
import com.together.newverse.data.firebase.getSingleValue
import com.together.newverse.data.firebase.model.OrderDto
import com.together.newverse.data.firebase.model.BuyerProfileDto
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.repository.OrderRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase implementation of OrderRepository
 * Based on universe project's DataRepositoryImpl
 */
class FirebaseOrderRepository : OrderRepository {

    init {
        Database.initialize()
    }

    /**
     * Observe orders for a seller in real-time
     */
    override fun observeSellerOrders(sellerId: String): Flow<List<Order>> = callbackFlow {
        println("🔥 FirebaseOrderRepository.observeSellerOrders: START with sellerId='$sellerId'")

        val ordersRef = Database.orderSeller(sellerId)
        val orders = mutableListOf<Order>()

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    println("🔥 onChildAdded: snapshot.key=${snapshot.key}, childrenCount=${snapshot.childrenCount}")
                    snapshot.children.forEach { orderSnapshot ->
                        try {
                            println("🔥 Processing order: key=${orderSnapshot.key}")
                            val dto = orderSnapshot.getValue(OrderDto::class.java)
                            if (dto != null) {
                                val order = dto.toDomain(orderSnapshot.key ?: "")
                                if (!order.hiddenBySeller) {
                                    orders.add(order)
                                    println("✅ Added order: ${order.id}")
                                } else {
                                    println("🙈 Skipped hidden order: ${order.id}")
                                }
                            } else {
                                println("⚠️ Order DTO is null for key=${orderSnapshot.key}")
                            }
                        } catch (e: Exception) {
                            println("❌ Error converting order ${orderSnapshot.key}: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    trySend(orders.toList())
                } catch (e: Exception) {
                    println("❌ Error in onChildAdded: ${e.message}")
                    e.printStackTrace()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    println("🔥 onChildChanged: snapshot.key=${snapshot.key}")
                    snapshot.children.forEach { orderSnapshot ->
                        try {
                            val dto = orderSnapshot.getValue(OrderDto::class.java)
                            if (dto != null) {
                                val order = dto.toDomain(orderSnapshot.key ?: "")
                                val index = orders.indexOfFirst { it.id == order.id }
                                if (order.hiddenBySeller) {
                                    // Remove from list if now hidden
                                    if (index >= 0) {
                                        orders.removeAt(index)
                                        println("🙈 Removed hidden order: ${order.id}")
                                    }
                                } else {
                                    // Update or add if not hidden
                                    if (index >= 0) {
                                        orders[index] = order
                                        println("✅ Updated order: ${order.id}")
                                    } else {
                                        orders.add(order)
                                        println("✅ Added order: ${order.id}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            println("❌ Error converting changed order ${orderSnapshot.key}: ${e.message}")
                        }
                    }
                    trySend(orders.toList())
                } catch (e: Exception) {
                    println("❌ Error in onChildChanged: ${e.message}")
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                try {
                    println("🔥 onChildRemoved: snapshot.key=${snapshot.key}")
                    snapshot.children.forEach { orderSnapshot ->
                        orders.removeAll { it.id == orderSnapshot.key }
                        println("✅ Removed order: ${orderSnapshot.key}")
                    }
                    trySend(orders.toList())
                } catch (e: Exception) {
                    println("❌ Error in onChildRemoved: ${e.message}")
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                println("❌ FirebaseOrderRepository.observeSellerOrders: Error - ${error.message}")
                close(error.toException())
            }
        }

        ordersRef.addChildEventListener(listener)

        awaitClose {
            println("🔥 FirebaseOrderRepository.observeSellerOrders: Removing listener")
            ordersRef.removeEventListener(listener)
        }
    }

    /**
     * Get buyer's placed orders
     */
    override suspend fun getBuyerOrders(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Result<List<Order>> {
        return try {
            println("🔥 FirebaseOrderRepository.getBuyerOrders: START")
            println("🔥 FirebaseOrderRepository.getBuyerOrders: sellerId=$sellerId")
            println("🔥 FirebaseOrderRepository.getBuyerOrders: placedOrderIds count=${placedOrderIds.size}")
            placedOrderIds.forEach { (date, orderId) ->
                println("🔥 FirebaseOrderRepository.getBuyerOrders:   - date=$date, orderId=$orderId")
            }

            val orders = mutableListOf<Order>()

            placedOrderIds.forEach { (date, orderId) ->
                println("🔥 FirebaseOrderRepository.getBuyerOrders: Loading order for date=$date, orderId=$orderId")
                val orderResult = loadOrder(sellerId, date, orderId)
                orderResult.onSuccess { order ->
                    println("✅ FirebaseOrderRepository.getBuyerOrders: Successfully loaded order $orderId with ${order.articles.size} items")
                    orders.add(order)
                }.onFailure { error ->
                    println("❌ FirebaseOrderRepository.getBuyerOrders: Failed to load order $orderId - ${error.message}")
                }
            }

            println("✅ FirebaseOrderRepository.getBuyerOrders: Loaded ${orders.size} orders total")
            Result.success(orders)
        } catch (e: Exception) {
            println("❌ FirebaseOrderRepository.getBuyerOrders: Error - ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Observe buyer's placed orders with real-time updates
     */
    override fun observeBuyerOrders(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Flow<List<Order>> = callbackFlow {
        println("🔥 FirebaseOrderRepository.observeBuyerOrders: START - ${placedOrderIds.size} orders")

        if (placedOrderIds.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Get target seller ID
        val targetSellerId = if (sellerId.isEmpty()) {
            Database.getFirstSellerIdRef().get().await().children.firstOrNull()?.key ?: "seller_001"
        } else {
            sellerId
        }

        val ordersRef = Database.orderSeller(targetSellerId)
        val orders = mutableMapOf<String, Order>()

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                processDateSnapshot(snapshot, orders, placedOrderIds)
                trySend(orders.values.toList())
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                processDateSnapshot(snapshot, orders, placedOrderIds)
                trySend(orders.values.toList())
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Remove orders from this date that belong to buyer
                val date = snapshot.key ?: return
                val orderId = placedOrderIds[date]
                if (orderId != null) {
                    orders.remove(orderId)
                    trySend(orders.values.toList())
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                println("❌ FirebaseOrderRepository.observeBuyerOrders: Error - ${error.message}")
                close(error.toException())
            }

            private fun processDateSnapshot(
                dateSnapshot: DataSnapshot,
                orders: MutableMap<String, Order>,
                placedOrderIds: Map<String, String>
            ) {
                val date = dateSnapshot.key ?: return
                val orderId = placedOrderIds[date] ?: return

                try {
                    val orderSnapshot = dateSnapshot.child(orderId)
                    if (orderSnapshot.exists()) {
                        val dto = orderSnapshot.getValue(OrderDto::class.java)
                        if (dto != null) {
                            val order = dto.toDomain(orderId)
                            if (!order.hiddenByBuyer) {
                                orders[orderId] = order
                                println("🔥 observeBuyerOrders: Updated order $orderId")
                            } else {
                                orders.remove(orderId)
                                println("🔥 observeBuyerOrders: Removed hidden order $orderId")
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("❌ observeBuyerOrders: Error processing order $orderId: ${e.message}")
                }
            }
        }

        ordersRef.addChildEventListener(listener)

        awaitClose {
            println("🔥 FirebaseOrderRepository.observeBuyerOrders: Removing listener")
            ordersRef.removeEventListener(listener)
        }
    }

    /**
     * Place a new order
     * Based on universe's sendOrder implementation
     */
    override suspend fun placeOrder(order: Order): Result<Order> {
        return try {
            println("🔥 FirebaseOrderRepository.placeOrder: START")

            // Get current user
            val currentUser = FirebaseAuth.getInstance().currentUser
                ?: return Result.failure(IllegalStateException("User not authenticated"))

            // Calculate date key from pickUpDate (format: yyyyMMdd)
            // Note: Order dates are already adjusted in BasketViewModel for dev mode
            val date = formatDateKey(order.pickUpDate)
            println("🔥 FirebaseOrderRepository.placeOrder: date='$date', pickUpDate=${order.pickUpDate}")

            // Get buyer profile to check if order already exists
            val buyerProfileSnapshot = Database.buyer().getSingleValue()
            val buyerProfileDto = buyerProfileSnapshot.getValue(BuyerProfileDto::class.java)
            val alreadyPlaced = buyerProfileDto?.placedOrderIds?.containsKey(date) == true

            if (alreadyPlaced) {
                println("⚠️ FirebaseOrderRepository.placeOrder: Order already exists for date $date")
                return Result.failure(AlreadyPlacedOrderException("Order already placed for this date"))
            }

            // Create reference for new order
            val ordersRef = Database.orderSeller(order.sellerId).child(date)
            val newOrderRef = ordersRef.push()
            val orderId = newOrderRef.key ?: return Result.failure(Exception("Failed to generate order ID"))

            println("🔥 FirebaseOrderRepository.placeOrder: orderId='$orderId'")

            // Convert to DTO and save
            val orderDto = OrderDto.fromDomain(order)
            newOrderRef.setValue(orderDto).await()

            // Update buyer profile with new order ID
            val updatedPlacedOrderIds = (buyerProfileDto?.placedOrderIds ?: emptyMap<String, String>()).toMutableMap()
            updatedPlacedOrderIds[date] = orderId

            val updatedBuyerProfile = BuyerProfileDto(
                displayName = order.buyerProfile.displayName,
                emailAddress = order.buyerProfile.emailAddress,
                telephoneNumber = order.buyerProfile.telephoneNumber,
                photoUrl = order.buyerProfile.photoUrl,
                anonymous = order.buyerProfile.anonymous,
                defaultMarket = order.buyerProfile.defaultMarket,
                defaultTime = order.buyerProfile.defaultPickUpTime,
                placedOrderIds = updatedPlacedOrderIds
            )

            Database.buyer().setValue(updatedBuyerProfile).await()

            println("✅ FirebaseOrderRepository.placeOrder: Success - orderId='$orderId'")
            Result.success(order.copy(id = orderId))

        } catch (e: Exception) {
            println("❌ FirebaseOrderRepository.placeOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update an existing order
     */
    override suspend fun updateOrder(order: Order): Result<Unit> {
        return try {
            val date = formatDateKey(order.pickUpDate)
            val orderDto = OrderDto.fromDomain(order)

            Database.orderSeller(order.sellerId)
                .child(date)
                .child(order.id)
                .setValue(orderDto)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ FirebaseOrderRepository.updateOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Cancel an order
     */
    override suspend fun cancelOrder(sellerId: String, date: String, orderId: String): Result<Boolean> {
        return try {
            Database.orderSeller(sellerId)
                .child(date)
                .child(orderId)
                .removeValue()
                .await()

            // Remove from buyer profile
            val buyerProfileSnapshot = Database.buyer().getSingleValue()
            val buyerProfileDto = buyerProfileSnapshot.getValue(BuyerProfileDto::class.java)
            if (buyerProfileDto != null) {
                val updatedOrderIds = buyerProfileDto.placedOrderIds.toMutableMap()
                updatedOrderIds.remove(date)

                val updatedProfile = BuyerProfileDto(
                    displayName = buyerProfileDto.displayName,
                    emailAddress = buyerProfileDto.emailAddress,
                    telephoneNumber = buyerProfileDto.telephoneNumber,
                    photoUrl = buyerProfileDto.photoUrl,
                    anonymous = buyerProfileDto.anonymous,
                    defaultMarket = buyerProfileDto.defaultMarket,
                    defaultTime = buyerProfileDto.defaultTime,
                    placedOrderIds = updatedOrderIds
                )
                Database.buyer().setValue(updatedProfile).await()
            }

            Result.success(true)
        } catch (e: Exception) {
            println("❌ FirebaseOrderRepository.cancelOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Hide an order from seller's view
     */
    override suspend fun hideOrderForSeller(sellerId: String, date: String, orderId: String): Result<Boolean> {
        return try {
            Database.orderSeller(sellerId)
                .child(date)
                .child(orderId)
                .child("hiddenBySeller")
                .setValue(true)
                .await()

            Result.success(true)
        } catch (e: Exception) {
            println("❌ FirebaseOrderRepository.hideOrderForSeller: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Hide an order from buyer's view
     */
    override suspend fun hideOrderForBuyer(sellerId: String, date: String, orderId: String): Result<Boolean> {
        return try {
            Database.orderSeller(sellerId)
                .child(date)
                .child(orderId)
                .child("hiddenByBuyer")
                .setValue(true)
                .await()

            Result.success(true)
        } catch (e: Exception) {
            println("❌ FirebaseOrderRepository.hideOrderForBuyer: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Load a specific order
     */
    override suspend fun loadOrder(sellerId: String, orderId: String, orderPath: String): Result<Order> {
        return try {
            val orderSnapshot = Database.orderSeller(sellerId)
                .child(orderId)
                .child(orderPath)
                .getSingleValue()

            val orderDto = orderSnapshot.getValue(OrderDto::class.java)

            if (orderDto != null) {
                Result.success(orderDto.toDomain(orderPath))
            } else {
                Result.failure(Exception("Order not found"))
            }
        } catch (e: Exception) {
            println("❌ FirebaseOrderRepository.loadOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get the most recent open/editable order for the current buyer
     * An order is considered editable if it's more than 3 days before pickup
     */
    override suspend fun getOpenEditableOrder(sellerId: String, placedOrderIds: Map<String, String>): Result<Order?> {
        return try {
            println("🔥 FirebaseOrderRepository.getOpenEditableOrder: START with ${placedOrderIds.size} orders")

            // Load all orders
            val orders = mutableListOf<Order>()
            placedOrderIds.forEach { (date, orderId) ->
                val orderResult = loadOrder(sellerId, date, orderId)
                orderResult.onSuccess { order ->
                    // Check if order is still editable (more than 3 days before pickup)
                    val threeDaysBeforePickup = order.pickUpDate - (3 * 24 * 60 * 60 * 1000)
                    val isEditable = System.currentTimeMillis() < threeDaysBeforePickup

                    if (isEditable) {
                        println("🔥 FirebaseOrderRepository.getOpenEditableOrder: Found editable order - orderId=$orderId, pickupDate=${order.pickUpDate}")
                        orders.add(order)
                    } else {
                        println("🔥 FirebaseOrderRepository.getOpenEditableOrder: Order not editable - orderId=$orderId (deadline passed)")
                    }
                }
            }

            // Return the most recent editable order (highest pickup date)
            val mostRecentOrder = orders.maxByOrNull { it.pickUpDate }

            if (mostRecentOrder != null) {
                println("✅ FirebaseOrderRepository.getOpenEditableOrder: Returning most recent order - orderId=${mostRecentOrder.id}")
            } else {
                println("✅ FirebaseOrderRepository.getOpenEditableOrder: No editable orders found")
            }

            Result.success(mostRecentOrder)
        } catch (e: Exception) {
            println("❌ FirebaseOrderRepository.getOpenEditableOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get the most recent upcoming order (regardless of editability)
     * Returns any order with pickup date in the future
     */
    override suspend fun getUpcomingOrder(sellerId: String, placedOrderIds: Map<String, String>): Result<Order?> {
        return try {
            println("🔥 FirebaseOrderRepository.getUpcomingOrder: START with ${placedOrderIds.size} orders")

            // Load all orders
            val orders = mutableListOf<Order>()
            placedOrderIds.forEach { (date, orderId) ->
                val orderResult = loadOrder(sellerId, date, orderId)
                orderResult.onSuccess { order ->
                    // Check if order pickup is in the future
                    val isUpcoming = order.pickUpDate > System.currentTimeMillis()

                    if (isUpcoming) {
                        println("🔥 FirebaseOrderRepository.getUpcomingOrder: Found upcoming order - orderId=$orderId, pickupDate=${order.pickUpDate}")
                        orders.add(order)
                    } else {
                        println("🔥 FirebaseOrderRepository.getUpcomingOrder: Order already passed - orderId=$orderId")
                    }
                }
            }

            // Return the most recent upcoming order (highest pickup date)
            val mostRecentOrder = orders.maxByOrNull { it.pickUpDate }

            if (mostRecentOrder != null) {
                println("✅ FirebaseOrderRepository.getUpcomingOrder: Returning most recent order - orderId=${mostRecentOrder.id}")
            } else {
                println("✅ FirebaseOrderRepository.getUpcomingOrder: No upcoming orders found")
            }

            Result.success(mostRecentOrder)
        } catch (e: Exception) {
            println("❌ FirebaseOrderRepository.getUpcomingOrder: Error - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Format timestamp to date key (yyyyMMdd)
     * Matches universe project's toOrderId() extension
     */
    private fun formatDateKey(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        return format.format(date)
    }
}

/**
 * Exception thrown when user tries to place an order for a date they already have an order
 */
class AlreadyPlacedOrderException(message: String) : Exception(message)
