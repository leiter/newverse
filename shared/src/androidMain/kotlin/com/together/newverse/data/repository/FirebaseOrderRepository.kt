package com.together.newverse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.together.newverse.data.firebase.Database
import com.together.newverse.data.firebase.awaitResult
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
                snapshot.children.forEach { orderSnapshot ->
                    val dto = orderSnapshot.getValue(OrderDto::class.java)
                    if (dto != null) {
                        val order = dto.toDomain(orderSnapshot.key ?: "")
                        orders.add(order)
                    }
                }
                trySend(orders.toList())
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.children.forEach { orderSnapshot ->
                    val dto = orderSnapshot.getValue(OrderDto::class.java)
                    if (dto != null) {
                        val order = dto.toDomain(orderSnapshot.key ?: "")
                        val index = orders.indexOfFirst { it.id == order.id }
                        if (index >= 0) {
                            orders[index] = order
                        }
                    }
                }
                trySend(orders.toList())
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                snapshot.children.forEach { orderSnapshot ->
                    orders.removeAll { it.id == orderSnapshot.key }
                }
                trySend(orders.toList())
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
            val date = formatDateKey(order.pickUpDate)
            println("🔥 FirebaseOrderRepository.placeOrder: date='$date'")

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
