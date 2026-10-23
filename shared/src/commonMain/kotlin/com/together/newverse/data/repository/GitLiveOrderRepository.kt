package com.together.newverse.data.repository

import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.DemoTransitionResult
import com.together.newverse.domain.model.DeliveryAddress
import com.together.newverse.domain.model.Fulfilment
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderOrigin
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.orderIndexWith
import com.together.newverse.domain.model.PriceCause
import com.together.newverse.domain.model.PriceComponent
import com.together.newverse.domain.model.canTransitionToProduction
import com.together.newverse.domain.model.statusAfterTransition
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.DataSnapshot
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import com.together.newverse.util.Log

/**
 * GitLive implementation of OrderRepository for cross-platform order management.
 * This version uses the correct GitLive Firebase SDK APIs.
 */
class GitLiveOrderRepository(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : OrderRepository {

    private companion object {
        private const val TAG = "OrderRepo"
        const val ORDERS_ROOT = "orders"
        const val DEMO_ORDERS_ROOT = "demo_orders"

        fun orderPath(sellerId: String, date: String, orderId: String) =
            "$ORDERS_ROOT/$sellerId/$date/$orderId"

        fun demoOrderPath(sellerId: String, date: String, orderId: String) =
            "$DEMO_ORDERS_ROOT/$sellerId/$date/$orderId"
    }

    // GitLive Firebase Database references
    private val database = Firebase.database
    private val rootRef = database.reference()
    private val ordersRootRef = database.reference(ORDERS_ROOT)
    private val demoOrdersRootRef = database.reference(DEMO_ORDERS_ROOT)

    private fun rootRef(isDemo: Boolean) = if (isDemo) demoOrdersRootRef else ordersRootRef

    // Cache for orders
    private val ordersCache = mutableMapOf<String, Order>()
    private val sellerOrdersCache = mutableMapOf<String, List<Order>>()

    /**
     * Observe orders for a seller with real-time updates.
     */
    override fun observeSellerOrders(sellerId: String): Flow<List<Order>> = flow {
        Log.d(TAG) { "observeSellerOrders: START for sellerId=$sellerId" }

        try {
            // Get the target seller ID - use default if empty
            val targetSellerId = if (sellerId.isEmpty()) {
                getFirstSellerId()
            } else {
                sellerId
            }

            // Create reference to seller's orders
            val ordersRef = ordersRootRef.child(targetSellerId)

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
                Log.d(TAG) { "observeSellerOrders: Emitting ${orders.size} orders" }
                emit(orders)
            }
        } catch (e: Exception) {
            Log.e(TAG) { "observeSellerOrders: Error - ${e.message}" }
            // Emit empty list on error
            emit(emptyList())
        }
    }

    /**
     * Observe buyer's placed orders with real-time updates.
     */
    override fun observeBuyerOrders(
        sellerId: String,
        placedOrderIds: Map<String, String>,
        isDemo: Boolean
    ): Flow<List<Order>> {
        Log.d(TAG) { "observeBuyerOrders: START - ${placedOrderIds.size} orders, isDemo=$isDemo" }

        if (placedOrderIds.isEmpty()) return flowOf(emptyList())

        val targetSellerId = sellerId.ifEmpty { getFirstSellerId() }

        // One listener per known order *per trunk* rather than one on the seller's
        // whole order tree: the rules only grant a buyer read on orders carrying their
        // own buyerId, so a subscription to the parent is rejected outright. A missing
        // or since-deleted order yields null and drops out of the list — which is also
        // how an order that simply does not live in this trunk reads.
        val perOrder = historyTrunks().flatMap { fromDemoTrunk ->
            val trunkRef = rootRef(fromDemoTrunk).child(targetSellerId)
            placedOrderIds.map { (date, orderId) ->
                trunkRef.child(date).child(orderId).valueEvents
                    .map { snapshot ->
                        if (!snapshot.exists) null
                        else mapSnapshotToOrder(snapshot)?.let { order ->
                            // The path is authoritative; see historyOrigin.
                            historyOrigin(fromDemoTrunk, order.origin)
                                ?.let { origin -> order.copy(origin = origin) }
                        }
                    }
                    .catch { e ->
                        Log.w(TAG) { "observeBuyerOrders: order $orderId unavailable - ${e.message}" }
                        emit(null)
                    }
            }
        }

        return combine(perOrder) { orders ->
            orders.filterNotNull()
                .filter { !it.hiddenByBuyer }
                // The real trunk is listened to first, so an order found in both is
                // kept as the real one.
                .distinctBy { it.id }
                .also { Log.d(TAG) { "observeBuyerOrders: Emitting ${it.size} orders" } }
        }
    }

    /**
     * File an order in the buyer's own index, so they can find it again.
     *
     * Best-effort by design: the order record is already written by the time this runs,
     * and failing to index it must not report the order as unplaced. A profile that
     * cannot be read is left alone rather than overwritten with a guess.
     */
    private suspend fun indexOrderForBuyer(dateString: String, orderId: String) {
        val profile = profileRepository.getBuyerProfile().getOrNull()
        if (profile == null) {
            Log.w(TAG) { "indexOrderForBuyer: no profile, $orderId left unindexed" }
            return
        }
        val updated = orderIndexWith(profile.placedOrderIds, dateString, orderId)
        if (updated == profile.placedOrderIds) return
        profileRepository.saveBuyerProfile(profile.copy(placedOrderIds = updated))
        Log.d(TAG) { "indexOrderForBuyer: $dateString -> $orderId" }
    }

    /**
     * Get first seller ID from database.
     */
    private fun getFirstSellerId(): String {
        return GitLiveArticleRepository.DEFAULT_SELLER_ID
    }

    /**
     * Get buyer's placed orders.
     */
    override suspend fun getBuyerOrders(
        sellerId: String,
        placedOrderIds: Map<String, String>,
        isDemo: Boolean
    ): Result<List<Order>> {
        return try {
            Log.d(TAG) { "getBuyerOrders: START - ${placedOrderIds.size} orders, isDemo=$isDemo" }

            if (placedOrderIds.isEmpty()) {
                return Result.success(emptyList())
            }

            // Get the target seller ID - use default if empty
            val targetSellerId = if (sellerId.isEmpty()) {
                getFirstSellerId()
            } else {
                sellerId
            }

            val orders = mutableListOf<Order>()

            // Fetch each order from GitLive Firebase, from every trunk this buyer reads
            // (both of them while they are in demo mode — see historyTrunks). The real
            // trunk goes first, so the guard below keeps the real copy of an order that
            // exists in both.
            historyTrunks().forEach { fromDemoTrunk ->
                placedOrderIds.forEach { (date, orderId) ->
                    if (orders.any { it.id == orderId }) return@forEach
                    try {
                        val orderRef = rootRef(fromDemoTrunk)
                            .child(targetSellerId).child(date).child(orderId)
                        val snapshot = orderRef.valueEvents.first()

                        if (snapshot.exists) {
                            val order = mapSnapshotToOrder(snapshot)
                            val origin = order?.let { historyOrigin(fromDemoTrunk, it.origin) }
                            if (order != null && origin != null) {
                                val finalOrder = order.copy(origin = origin)
                                orders.add(finalOrder)
                                ordersCache[orderId] = finalOrder
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG) { "getBuyerOrders: Failed to fetch order $orderId: ${e.message}" }
                    }
                }
            }

            Log.d(TAG) { "getBuyerOrders: Found ${orders.size} orders after filtering" }
            Result.success(orders)

        } catch (e: Exception) {
            Log.e(TAG) { "getBuyerOrders: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Place a new order.
     * - New orders (empty id): Uses Firebase push() to generate auto-ID
     * - Existing orders: Uses setValue() with the existing Firebase order ID
     */
    override suspend fun placeOrder(order: Order): Result<Order> {
        return try {
            Log.d(TAG) { "placeOrder: START" }

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                return Result.failure(Exception("User not authenticated"))
            }

            // Format date for Firebase path
            val dateString = formatDate(order.pickUpDate)

            // Get the target seller ID - use default if empty
            val targetSellerId = if (order.sellerId.isEmpty()) {
                getFirstSellerId()
            } else {
                order.sellerId
            }

            // Get reference to the date node (demo orders go to separate path)
            val dateRef = rootRef(order.origin.isDemo).child(targetSellerId).child(dateString)

            // Determine order ID and reference based on whether this is a new order
            val (orderId, orderRef) = if (order.id.isEmpty()) {
                // New order: use push() to get Firebase auto-generated ID
                val newOrderRef = dateRef.push()
                val newOrderId = newOrderRef.key ?: "order_${Clock.System.now().toEpochMilliseconds()}"
                Log.d(TAG) { "placeOrder: Using push() - generated ID: $newOrderId" }
                Pair(newOrderId, newOrderRef)
            } else {
                // Existing order: use the existing Firebase order ID
                Log.d(TAG) { "placeOrder: Using existing ID: ${order.id}" }
                Pair(order.id, dateRef.child(order.id))
            }

            val finalOrder = order.copy(
                id = orderId,
                sellerId = targetSellerId,
                createdDate = if (order.createdDate == 0L) Clock.System.now().toEpochMilliseconds() else order.createdDate,
                status = if (order.status == OrderStatus.DRAFT) OrderStatus.PLACED else order.status
            )

            // Convert to map for Firebase
            val orderMap = orderToMap(finalOrder)

            // Save to GitLive Firebase using setValue
            orderRef.setValue(orderMap)

            // File it in the buyer's index, which is the only way they can find it.
            indexOrderForBuyer(dateString, orderId)

            // Cache the order
            ordersCache[orderId] = finalOrder

            Log.d(TAG) { "placeOrder: Success - orderId=$orderId" }
            Result.success(finalOrder)

        } catch (e: Exception) {
            Log.e(TAG) { "placeOrder: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Update an existing order.
     * Uses setValue() with the existing Firebase order ID.
     */
    override suspend fun updateOrder(order: Order): Result<Unit> {
        return try {
            Log.d(TAG) { "updateOrder: START - orderId=${order.id}" }

            // A stored order has been placed; callers rebuilding it from the basket
            // leave the status at its DRAFT default. As in placeOrder, store it as
            // PLACED — which also makes the deadline check below apply.
            val placed = if (order.status == OrderStatus.DRAFT) {
                order.copy(status = OrderStatus.PLACED)
            } else order

            if (!placed.canEdit()) {
                return Result.failure(Exception("Order is not editable (deadline passed or status: ${placed.status})"))
            }

            // Get the target seller ID - use default if empty
            val targetSellerId = placed.sellerId.ifEmpty {
                getFirstSellerId()
            }

            // Format date for Firebase path
            val dateString = formatDate(placed.pickUpDate)

            // Convert to map for Firebase
            val orderMap = orderToMap(placed)

            // Save to GitLive Firebase using the existing order ID
            val orderRef = rootRef(placed.origin.isDemo).child(targetSellerId).child(dateString).child(placed.id)
            orderRef.setValue(orderMap)

            // An edit has to file the order too. It used to assume the entry was already
            // there from placing it, which fails for an order that was deleted and saved
            // again, and for one whose pickup day moved to another date's path.
            indexOrderForBuyer(dateString, placed.id)

            // Update cache
            ordersCache[placed.id] = placed

            Log.d(TAG) { "updateOrder: Success" }
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG) { "updateOrder: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Cancel an order.
     */
    override suspend fun cancelOrder(
        sellerId: String,
        date: String,
        orderId: String,
        isDemo: Boolean
    ): Result<Boolean> {
        return try {
            val rootName = if (isDemo) DEMO_ORDERS_ROOT else ORDERS_ROOT
            val path = "$rootName/$sellerId/$date/$orderId"
            Log.d(TAG) { "cancelOrder: START" }
            Log.d(TAG) { "cancelOrder: sellerId=$sellerId" }
            Log.d(TAG) { "cancelOrder: date=$date" }
            Log.d(TAG) { "cancelOrder: orderId=$orderId" }
            Log.d(TAG) { "cancelOrder: Full path=$path" }

            // Fetch the order from GitLive Firebase
            val orderRef = rootRef(isDemo).child(sellerId).child(date).child(orderId)
            val snapshot = orderRef.valueEvents.first()
            Log.d(TAG) { "cancelOrder: snapshot.exists=${snapshot.exists}" }

            if (snapshot.exists) {
                val order = mapSnapshotToOrder(snapshot)
                if (order != null) {
                    // Validate order can still be modified (deadline check)
                    if (!order.canEdit()) {
                        Log.e(TAG) { "cancelOrder: Order cannot be cancelled (deadline passed)" }
                        return Result.failure(Exception("Order cannot be cancelled (deadline passed)"))
                    }

                    // Update status to cancelled
                    val cancelledOrder = order.copy(status = OrderStatus.CANCELLED)
                    val orderMap = orderToMap(cancelledOrder)

                    // Save back to Firebase
                    orderRef.setValue(orderMap)

                    // Update cache
                    ordersCache[orderId] = cancelledOrder

                    Log.d(TAG) { "cancelOrder: Success" }
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to parse order data"))
                }
            } else {
                Log.e(TAG) { "cancelOrder: Order not found in Firebase" }
                // Clear from cache since it doesn't exist in Firebase
                ordersCache.remove(orderId)
                Log.d(TAG) { "cancelOrder: Removed from cache" }
                Result.failure(Exception("Order not found"))
            }

        } catch (e: Exception) {
            Log.e(TAG) { "cancelOrder: Error - ${e.message}" }
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
            Log.d(TAG) { "loadOrder: START - orderId=$orderId" }

            // Check cache first
            ordersCache[orderId]?.let {
                Log.d(TAG) { "loadOrder: Found in cache" }
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

                    Log.d(TAG) { "loadOrder: Fetched from Firebase" }
                    Result.success(order)
                } else {
                    Result.failure(Exception("Failed to parse order data"))
                }
            } else {
                Log.e(TAG) { "loadOrder: Order not found" }
                Result.failure(Exception("Order not found"))
            }

        } catch (e: Exception) {
            Log.e(TAG) { "loadOrder: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Get the most recent open/editable order for the current buyer.
     */
    override suspend fun getOpenEditableOrder(
        sellerId: String,
        placedOrderIds: Map<String, String>,
        isDemo: Boolean
    ): Result<Order?> {
        return try {
            Log.d(TAG) { "getOpenEditableOrder: START" }

            if (placedOrderIds.isEmpty()) {
                Log.d(TAG) { "getOpenEditableOrder: No placed orders" }
                return Result.success(null)
            }

            // Get all buyer orders
            val ordersResult = getBuyerOrders(sellerId, placedOrderIds, isDemo)
            if (ordersResult.isFailure) {
                return Result.failure(ordersResult.exceptionOrNull()!!)
            }

            val orders = ordersResult.getOrThrow()
            val now = Clock.System.now().toEpochMilliseconds()

            // Find most recent editable order (must be editable AND pickup date in future)
            val editableOrder = orders
                .filter { it.pickUpDate > now && it.canEdit() }
                .maxByOrNull { it.createdDate }

            if (editableOrder != null) {
                Log.d(TAG) { "getOpenEditableOrder: Found editable order ${editableOrder.id}" }
            } else {
                Log.d(TAG) { "getOpenEditableOrder: No editable orders found" }
            }

            Result.success(editableOrder)

        } catch (e: Exception) {
            Log.e(TAG) { "getOpenEditableOrder: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Get the most recent upcoming order (regardless of editability).
     */
    override suspend fun getUpcomingOrder(
        sellerId: String,
        placedOrderIds: Map<String, String>,
        isDemo: Boolean
    ): Result<Order?> {
        return try {
            Log.d(TAG) { "getUpcomingOrder: START" }

            if (placedOrderIds.isEmpty()) {
                Log.d(TAG) { "getUpcomingOrder: No placed orders" }
                return Result.success(null)
            }

            // Get all buyer orders
            val ordersResult = getBuyerOrders(sellerId, placedOrderIds, isDemo)
            if (ordersResult.isFailure) {
                return Result.failure(ordersResult.exceptionOrNull()!!)
            }

            val orders = ordersResult.getOrThrow()
            val now = Clock.System.now().toEpochMilliseconds()

            // Find most recent upcoming order (pickup date in the future)
            val upcomingOrder = orders
                .filter { it.pickUpDate > now }
                .maxByOrNull { it.pickUpDate }

            if (upcomingOrder != null) {
                Log.d(TAG) { "getUpcomingOrder: Found upcoming order ${upcomingOrder.id}" }
            } else {
                Log.d(TAG) { "getUpcomingOrder: No upcoming orders found" }
            }

            Result.success(upcomingOrder)

        } catch (e: Exception) {
            Log.e(TAG) { "getUpcomingOrder: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    // Helper functions

    /**
     * Format a timestamp to a date string for Firebase paths.
     */
    private fun formatDate(timestamp: Long): String {
        // Convert timestamp to YYYYMMDD format (no dashes for Firebase path)
        val date = Instant.fromEpochMilliseconds(timestamp)
        val timezone = TimeZone.currentSystemDefault()
        val localDateTime = date.toLocalDateTime(timezone)
        return "${localDateTime.year}${localDateTime.month.number.toString().padStart(2, '0')}${localDateTime.day.toString().padStart(2, '0')}"
    }

    /**
     * Map a DataSnapshot to an Order domain model.
     */
    private fun mapSnapshotToOrder(snapshot: DataSnapshot): Order? {
        val orderId = snapshot.key ?: return null
        return when (val value = snapshot.value) {
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
                                // Absent on every order written before per-market
                                // pricing. Left at 0.0 so OrderedProduct.basePriceOrPrice
                                // reads it back as unadjusted rather than as free.
                                basePrice = (articleData["basePrice"] as? Number)?.toDouble() ?: 0.0,
                                priceComponents = storedPriceComponents(articleData["priceComponents"]),
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
                        // Absent on every order written before delivery existed, which
                        // reads as PICKUP — those orders were all collected at a stall.
                        fulfilment = Fulfilment.stored(value["fulfilment"] as? String),
                        deliveryAddress = storedDeliveryAddress(value["deliveryAddress"]),
                        pricingRevision = (value["pricingRevision"] as? Number)?.toLong() ?: 0L,
                        pickUpDate = (value["pickUpDate"] as? Number)?.toLong() ?: 0L,
                        message = value["message"] as? String ?: "",
                        notFavourite = value["notFavourite"] as? Boolean != false,
                        articles = articles,
                        status = storedOrderStatus(value["status"] as? String),
                        hiddenBySeller = value["hiddenBySeller"] as? Boolean == true,
                        hiddenByBuyer = value["hiddenByBuyer"] as? Boolean == true,
                        origin = storedOrderOrigin(
                            raw = value["origin"] as? String,
                            legacyIsDemoOrder = value["isDemoOrder"] as? Boolean,
                            legacyStatus = value["status"] as? String
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG) { "Error mapping order snapshot: ${e.message}" }
                    null
                }
            }
            else -> null
        }
    }

    /** Read back a delivery address; absent or unreadable gives [DeliveryAddress.NONE]. */
    private fun storedDeliveryAddress(raw: Any?): DeliveryAddress {
        val data = raw as? Map<*, *> ?: return DeliveryAddress.NONE
        return DeliveryAddress(
            street = data["street"] as? String ?: "",
            houseNumber = data["houseNumber"] as? String ?: "",
            zipCode = data["zipCode"] as? String ?: "",
            city = data["city"] as? String ?: "",
            note = data["note"] as? String ?: ""
        )
    }

    /**
     * Read back a stored price breakdown.
     *
     * A component whose cause is not one this build knows is dropped rather than
     * guessed at: the enum may gain entries, and an older app reading a newer order
     * must not invent a cause. The gross price is stored in its own right, so a
     * dropped component costs the explanation, never the total.
     */
    private fun storedPriceComponents(raw: Any?): List<PriceComponent> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            val data = entry as? Map<*, *> ?: return@mapNotNull null
            val cause = (data["cause"] as? String)
                ?.let { name -> PriceCause.entries.firstOrNull { it.name == name } }
                ?: return@mapNotNull null
            PriceComponent(
                cause = cause,
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                note = data["note"] as? String ?: ""
            )
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
            "fulfilment" to order.fulfilment.name,
            // Written only for a delivery. A pickup order has no address to carry, and
            // the buyer's postal address is not data to scatter into every order it is
            // irrelevant to — see `doc/reports/buyer-profile-data-safety.md`.
            "deliveryAddress" to order.deliveryAddress
                .takeIf { order.isDelivery && !it.isEmpty }
                ?.let { address ->
                    mapOf(
                        "street" to address.street,
                        "houseNumber" to address.houseNumber,
                        "zipCode" to address.zipCode,
                        "city" to address.city,
                        "note" to address.note
                    )
                },
            "pricingRevision" to order.pricingRevision,
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
                    // Written only where it says something: an unadjusted item would
                    // otherwise carry a basePrice equal to price and an empty list on
                    // every order, for no reader.
                    "basePrice" to article.basePrice.takeIf { it > 0.0 },
                    "priceComponents" to article.priceComponents
                        .takeIf { it.isNotEmpty() }
                        ?.map { component ->
                            mapOf(
                                "cause" to component.cause.name,
                                "amount" to component.amount,
                                "note" to component.note
                            )
                        },
                    "amount" to article.amount,
                    "amountCount" to article.amountCount,
                    "piecesCount" to article.piecesCount
                )
            },
            "status" to order.status.name,
            "hiddenBySeller" to order.hiddenBySeller,
            "hiddenByBuyer" to order.hiddenByBuyer,
            "origin" to order.origin.name
        )
    }

    override suspend fun hideOrderForSeller(sellerId: String, date: String, orderId: String): Result<Boolean> {
        return try {
            Log.d(TAG) { "hideOrderForSeller: START - orderId=$orderId" }

            val orderRef = ordersRootRef.child(sellerId).child(date).child(orderId).child("hiddenBySeller")
            orderRef.setValue(true)

            Log.d(TAG) { "hideOrderForSeller: Success" }
            Result.success(true)

        } catch (e: Exception) {
            Log.e(TAG) { "hideOrderForSeller: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun hideOrderForBuyer(
        sellerId: String,
        date: String,
        orderId: String,
        isDemo: Boolean
    ): Result<Boolean> {
        return try {
            Log.d(TAG) { "hideOrderForBuyer: START - orderId=$orderId, isDemo=$isDemo" }

            val orderRef = rootRef(isDemo).child(sellerId).child(date).child(orderId).child("hiddenByBuyer")
            orderRef.setValue(true)

            Log.d(TAG) { "hideOrderForBuyer: Success" }
            Result.success(true)

        } catch (e: Exception) {
            Log.e(TAG) { "hideOrderForBuyer: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Update only the status of an order (lightweight update for status transitions).
     */
    override suspend fun updateOrderStatus(
        sellerId: String,
        date: String,
        orderId: String,
        status: OrderStatus,
        isDemo: Boolean
    ): Result<Unit> {
        return try {
            Log.d(TAG) { "updateOrderStatus: START - orderId=$orderId, newStatus=$status, isDemo=$isDemo" }

            val orderRef = rootRef(isDemo).child(sellerId).child(date).child(orderId).child("status")
            orderRef.setValue(status.name)

            // Update cache if order exists
            ordersCache[orderId]?.let { cachedOrder ->
                ordersCache[orderId] = cachedOrder.copy(status = status)
            }

            Log.d(TAG) { "updateOrderStatus: Success" }
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG) { "updateOrderStatus: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Move a buyer's demo orders into production in one atomic multi-path update.
     *
     * Both halves of the move are expressed as paths against the database root — the new
     * record under `orders/`, the removal under `demo_orders/` — so an order is never
     * written in one place and left behind in the other. Same shape as the two-half article
     * write in `ArticleNodes`.
     */
    override suspend fun transitionDemoOrdersToProduction(
        sellerId: String,
        remoteOrderIds: Map<String, String>,
        localOrders: List<Order>,
        allowedMarkets: List<String>
    ): Result<DemoTransitionResult> {
        return try {
            Log.d(TAG) { "transitionDemoOrders: START - ${remoteOrderIds.size} remote, ${localOrders.size} local" }

            val userId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            val targetSellerId = sellerId.ifEmpty { getFirstSellerId() }
            val now = Clock.System.now()
            val update = mutableMapOf<String, Any?>()
            val transitioned = mutableMapOf<String, String>()
            val dropped = mutableMapOf<String, String>()
            var expired = 0
            val notEligible = mutableSetOf<String>()

            // Empty means all, as it does everywhere a scope is read.
            fun mayCollectAt(marketId: String) =
                allowedMarkets.isEmpty() || marketId.isEmpty() || marketId in allowedMarkets

            // Orders already in Firebase: read, re-key under orders/, drop from demo_orders/.
            remoteOrderIds.forEach { (date, orderId) ->
                val demoPath = demoOrderPath(targetSellerId, date, orderId)
                val snapshot = demoOrdersRootRef.child(targetSellerId).child(date).child(orderId)
                    .valueEvents.first()
                if (!snapshot.exists) {
                    Log.d(TAG) { "transitionDemoOrders: $date/$orderId is gone, nothing to move" }
                    return@forEach
                }
                // Whatever the record says, it came out of demo_orders/ — it is a demo order.
                val order = mapSnapshotToOrder(snapshot)?.copy(origin = OrderOrigin.DEMO)
                if (order == null) {
                    // Left where it is: a record this version cannot read is not one to
                    // delete. deleteOldDemoOrders sweeps it up after a month.
                    Log.w(TAG) { "transitionDemoOrders: $date/$orderId unreadable, left in place" }
                    return@forEach
                }
                // Checked before the demo copy is scheduled for deletion: an order we
                // refuse to move has to still be there afterwards.
                if (!mayCollectAt(order.marketId)) {
                    notEligible += order.marketId
                    Log.d(TAG) { "transitionDemoOrders: $date/$orderId is for market ${order.marketId}, not granted — left in place" }
                    return@forEach
                }
                update[demoPath] = null
                if (!order.canTransitionToProduction(now)) {
                    // Deleted but not moved, so its index entry is now dangling and is
                    // the one thing a transition may prune.
                    expired++
                    dropped[date] = orderId
                    Log.d(TAG) { "transitionDemoOrders: $date/$orderId pickup has passed, not moved" }
                    return@forEach
                }
                update[orderPath(targetSellerId, date, orderId)] = orderToMap(order.toProduction(userId, now))
                transitioned[date] = orderId
            }

            // Orders held only on this device: they have no Firebase id yet, so mint one.
            localOrders.forEach { local ->
                if (!mayCollectAt(local.marketId)) {
                    notEligible += local.marketId
                    Log.d(TAG) { "transitionDemoOrders: local ${local.id} is for market ${local.marketId}, not granted — left in place" }
                    return@forEach
                }
                if (!local.canTransitionToProduction(now)) {
                    expired++
                    Log.d(TAG) { "transitionDemoOrders: local ${local.id} pickup has passed, not moved" }
                    return@forEach
                }
                val date = formatDate(local.pickUpDate)
                val orderId = ordersRootRef.child(targetSellerId).child(date).push().key
                    ?: return Result.failure(IllegalStateException("Failed to generate order ID"))
                val order = local.copy(id = orderId, sellerId = targetSellerId).toProduction(userId, now)
                update[orderPath(targetSellerId, date, orderId)] = orderToMap(order)
                transitioned[date] = orderId
            }

            val result = DemoTransitionResult(
                transitioned = transitioned,
                dropped = dropped,
                expired = expired,
                notEligibleMarketIds = notEligible.toList()
            )

            if (update.isEmpty()) {
                Log.d(TAG) { "transitionDemoOrders: nothing to move (${notEligible.size} market(s) not granted)" }
                return Result.success(result)
            }

            rootRef.updateChildren(update)

            Log.d(TAG) {
                "transitionDemoOrders: moved ${transitioned.size} order(s), $expired past pickup dropped, " +
                    "${notEligible.size} market(s) not granted and left in place"
            }
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG) { "transitionDemoOrders: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * A demo order as it should look once it lives under `orders/`.
     *
     * The buyer id is restamped with the current uid. A demo order is usually placed while
     * anonymous, and an anonymous account that signs in with a provider rather than being
     * linked comes back with a different uid (hence `migrateAnonymousUser`). Both the write
     * rule and the buyer's own read rule on `orders/` key on `buyerId === auth.uid`, so
     * carrying the stale uid over would have the whole update rejected — or, if it slipped
     * through, leave the buyer unable to read the order they just had adopted.
     */
    private fun Order.toProduction(userId: String, now: Instant) = copy(
        origin = OrderOrigin.TRANSITIONED,
        status = statusAfterTransition(now),
        buyerProfile = buyerProfile.copy(id = userId)
    )

    /**
     * Delete demo orders older than 1 month from demo_orders path.
     */
    override suspend fun deleteOldDemoOrders(sellerId: String): Result<Int> {
        return try {
            Log.d(TAG) { "deleteOldDemoOrders: START for sellerId=$sellerId" }

            val sellerDemoRef = demoOrdersRootRef.child(sellerId)
            val snapshot = sellerDemoRef.valueEvents.first()

            if (!snapshot.exists) {
                Log.d(TAG) { "deleteOldDemoOrders: No demo orders found" }
                return Result.success(0)
            }

            val now = Clock.System.now()
            val timezone = TimeZone.currentSystemDefault()
            now.toLocalDateTime(timezone)
            // Cutoff: 1 month ago (approximate: 30 days)
            val cutoffMillis = now.toEpochMilliseconds() - (30L * 24 * 60 * 60 * 1000)

            var deletedCount = 0
            snapshot.children.forEach { dateSnapshot ->
                val dateKey = dateSnapshot.key ?: return@forEach
                if (dateKey.length == 8) {
                    try {
                        val year = dateKey.substring(0, 4).toInt()
                        val month = dateKey.substring(4, 6).toInt()
                        val day = dateKey.substring(6, 8).toInt()
                        val localDate = LocalDate(year, month, day)
                        val dateMillis = localDate.atStartOfDayIn(timezone).toEpochMilliseconds()
                        if (dateMillis < cutoffMillis) {
                            sellerDemoRef.child(dateKey).removeValue()
                            deletedCount++
                            Log.d(TAG) { "deleteOldDemoOrders: Deleted $dateKey" }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG) { "deleteOldDemoOrders: Failed to parse date key '$dateKey': ${e.message}" }
                    }
                }
            }

            Log.d(TAG) { "deleteOldDemoOrders: Deleted $deletedCount date nodes" }
            Result.success(deletedCount)

        } catch (e: Exception) {
            Log.e(TAG) { "deleteOldDemoOrders: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Delete specific demo orders from Firebase demo_orders path.
     */
    override suspend fun deleteDemoOrders(sellerId: String, dateToOrderId: Map<String, String>): Result<Unit> {
        return try {
            Log.d(TAG) { "deleteDemoOrders: Deleting ${dateToOrderId.size} orders for sellerId=$sellerId" }
            dateToOrderId.forEach { (date, orderId) ->
                demoOrdersRootRef.child(sellerId).child(date).child(orderId).removeValue()
                Log.d(TAG) { "deleteDemoOrders: Deleted $date/$orderId" }
            }
            Log.d(TAG) { "deleteDemoOrders: Success" }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG) { "deleteDemoOrders: Error - ${e.message}" }
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

/**
 * The status of an order read from the database.
 *
 * Every order under /orders has been placed — drafts live only in the buyer's basket.
 * Yet updateOrder used to store orders edited by the buyer as DRAFT, and a DRAFT is
 * editable at any time, past the Tuesday deadline. So a stored DRAFT, a missing
 * status or one this app version does not know are all read as PLACED; the usual
 * date-based transitions then take it to LOCKED or COMPLETED.
 */
/**
 * The [OrderOrigin] of a stored order record.
 *
 * Records written before the origin field existed carry the demo flag in one of two older
 * shapes — the boolean `isDemoOrder`, or the status value `DEMO_ORDER` — and both are read
 * back as [OrderOrigin.DEMO]. Everything else, including an unknown value written by a
 * newer app version, reads as [OrderOrigin.PRODUCTION]: the ordinary case, and the one that
 * keeps an unrecognised order visible and bookable rather than silently filtered away.
 */
internal fun storedOrderOrigin(
    raw: String?,
    legacyIsDemoOrder: Boolean? = null,
    legacyStatus: String? = null
): OrderOrigin {
    OrderOrigin.entries.find { it.name == raw }?.let { return it }
    if (legacyIsDemoOrder == true || legacyStatus == LEGACY_DEMO_STATUS) return OrderOrigin.DEMO
    return OrderOrigin.PRODUCTION
}

/** The status value that used to carry demo-ness before [OrderOrigin] existed. */
internal const val LEGACY_DEMO_STATUS = "DEMO_ORDER"

/**
 * The trunks a buyer's own order history is read from, real trunk first.
 *
 * Both, always, whatever the buyer's access status. Demo mode governs what a buyer may
 * *place*, not what they may *see* of their own past, and neither trunk is exclusive to
 * one state:
 *
 *  - unapproved, they still hold the real orders they placed before approval existed,
 *    or before they lost it;
 *  - approved, they can still hold demo orders, because a transition deliberately
 *    leaves behind any order placed for a market they turn out not to be scoped to —
 *    `DemoTransitionResult.notEligibleMarketIds` exists to say so, and "the buyer is
 *    asked" is impossible if the order they are asked about is unreadable.
 *
 * This read needs no rules change: the order read rule grants a buyer every order
 * carrying their own `buyerId` and never consults their access status.
 *
 * Real first, because [combine] preserves this order and the de-duplication downstream
 * keeps the first of a repeated id — so an order present in both trunks, which is a
 * transition that did not finish, is taken from the trunk that counts.
 */
internal fun historyTrunks(): List<Boolean> = listOf(false, true)

/**
 * The origin to report for one history record, or null to leave it out.
 *
 * The path decides, as it does everywhere else: a record under `demo_orders/` is a demo
 * order whatever it stores, and a record under `/orders` keeps the provenance it stores,
 * so [OrderOrigin.TRANSITIONED] survives being read back. A record under `/orders` that
 * still reads as [OrderOrigin.DEMO] is a legacy half-state and is dropped rather than
 * shown, which is what it was before a demo buyer read this trunk at all.
 */
internal fun historyOrigin(fromDemoTrunk: Boolean, stored: OrderOrigin): OrderOrigin? = when {
    fromDemoTrunk -> OrderOrigin.DEMO
    stored.isDemo -> null
    else -> stored
}

internal fun storedOrderStatus(raw: String?): OrderStatus {
    val status = raw?.let { name -> OrderStatus.entries.find { it.name == name } }
    return if (status == null || status == OrderStatus.DRAFT) OrderStatus.PLACED else status
}
