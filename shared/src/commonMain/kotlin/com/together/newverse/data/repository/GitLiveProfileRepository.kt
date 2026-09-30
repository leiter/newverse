package com.together.newverse.data.repository

import com.together.newverse.data.config.PendingInviteTokenStorage
import com.together.newverse.domain.model.AccessRequest
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.BuyerAccess
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.CleanUpResult
import com.together.newverse.domain.model.DraftBasket
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.util.Log
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.DataSnapshot
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * GitLive implementation of ProfileRepository for cross-platform profile management.
 * This version uses the correct GitLive Firebase SDK APIs.
 */
class GitLiveProfileRepository(
    private val authRepository: AuthRepository,
    private val pendingTokenStorage: PendingInviteTokenStorage? = null
) : ProfileRepository {

    private companion object {
        private const val TAG = "ProfileRepo"
    }

    // GitLive Firebase Database references
    private val database = Firebase.database
    private val buyersRef = database.reference("buyer_profile")
    private val sellersRef = database.reference("seller_profile")
    private val accessRequestsRef = database.reference("access_requests")
    private val buyerAccessStatusRef = database.reference("buyer_access_status")
    private val inviteTokensRef = database.reference("invite_tokens")

    // Local cache for performance
    private val _buyerProfile = MutableStateFlow<BuyerProfile?>(null)
    private val sellerProfileCache = mutableMapOf<String, SellerProfile>()

    override fun observeBuyerProfile(): Flow<BuyerProfile?> {
        Log.d(TAG) { "observeBuyerProfile: Setting up profile observer" }
        return _buyerProfile.asStateFlow()
    }

    override suspend fun getBuyerProfile(): Result<BuyerProfile> {
        return try {
            Log.d(TAG) { "getBuyerProfile: START" }

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG) { "getBuyerProfile: No authenticated user" }
                return Result.failure(Exception("User not authenticated"))
            }

            // Check cache first
            val cachedProfile = _buyerProfile.value
            if (cachedProfile != null) {
                Log.d(TAG) { "getBuyerProfile: Returning cached profile" }
                return Result.success(cachedProfile)
            }

            // Fetch from GitLive Firebase using valueEvents Flow
            val snapshot = buyersRef.child(userId).valueEvents.first()

            // Check if data exists
            if (snapshot.exists) {
                val profile = mapSnapshotToBuyerProfile(userId, snapshot)
                _buyerProfile.value = profile
                Log.d(TAG) { "getBuyerProfile: Fetched from Firebase" }
                Result.success(profile)
            } else {
                // Create default profile for new users
                val defaultProfile = createDefaultBuyerProfile(userId)
                _buyerProfile.value = defaultProfile
                Log.d(TAG) { "getBuyerProfile: Created default profile" }
                Result.success(defaultProfile)
            }

        } catch (e: Exception) {
            Log.e(TAG) { "getBuyerProfile: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun saveBuyerProfile(profile: BuyerProfile): Result<BuyerProfile> {
        return try {
            Log.d(TAG) { "saveBuyerProfile: START - ${profile.displayName}" }

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG) { "saveBuyerProfile: No authenticated user" }
                return Result.failure(Exception("User not authenticated"))
            }

            // Ensure profile ID matches authenticated user
            val profileWithCorrectId = profile.copy(id = userId)

            // Convert to map for Firebase
            val profileMap = buyerProfileToMap(profileWithCorrectId)

            // Save to GitLive Firebase. updateChildren (not setValue) so this only touches
            // the fields this profile model knows about, leaving sibling children written
            // by other flows - e.g. linkedSellerIds from submitAccessRequest - untouched.
            buyersRef.child(userId).updateChildren(profileMap)

            // Update local cache
            val previousDisplayName = _buyerProfile.value?.displayName
            _buyerProfile.value = profileWithCorrectId

            if (profileWithCorrectId.displayName != previousDisplayName) {
                syncDisplayNameToLinkedSellers(userId, profileWithCorrectId.displayName)
            }

            Log.d(TAG) { "saveBuyerProfile: Success" }
            Result.success(profileWithCorrectId)

        } catch (e: Exception) {
            Log.e(TAG) { "saveBuyerProfile: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun clearCache() {
        _buyerProfile.value = null
        sellerProfileCache.clear()
        Log.d(TAG) { "clearCache: Cleared cached profiles" }
    }

    override suspend fun getSellerDisplayName(sellerId: String): Result<String> {
        return try {
            if (sellerId.isEmpty()) return Result.success("")
            sellerProfileCache[sellerId]?.let { return Result.success(it.displayName) }

            // Read the single public child: the buyer has no read access to the
            // seller profile node itself, which carries the client lists.
            val snapshot = sellersRef.child(sellerId).child("displayName").valueEvents.first()
            val displayName = snapshot.value as? String ?: ""
            Result.success(displayName)
        } catch (e: Exception) {
            Log.e(TAG) { "getSellerDisplayName: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun getSellerProfile(sellerId: String): Result<SellerProfile> {
        return try {
            Log.d(TAG) { "getSellerProfile: START - sellerId=$sellerId" }

            // Check cache first
            if (sellerId.isNotEmpty() && sellerProfileCache.containsKey(sellerId)) {
                Log.d(TAG) { "getSellerProfile: Returning cached profile" }
                return Result.success(sellerProfileCache[sellerId]!!)
            }

            val targetSellerId = if (sellerId.isEmpty()) {
                // Get first seller for buyers
                val sellersSnapshot = sellersRef.valueEvents.first()

                // Get first child key
                val firstSellerKey = sellersSnapshot.children.firstOrNull()?.key ?: "seller_001"
                firstSellerKey
            } else {
                sellerId
            }

            // Fetch specific seller from GitLive Firebase
            val snapshot = sellersRef.child(targetSellerId).valueEvents.first()

            if (snapshot.exists) {
                val profile = mapSnapshotToSellerProfile(targetSellerId, snapshot)
                sellerProfileCache[targetSellerId] = profile
                Log.d(TAG) { "getSellerProfile: Fetched from Firebase" }
                Result.success(profile)
            } else {
                // Return mock for testing if no seller found
                val mockProfile = createMockSellerProfile(targetSellerId)
                sellerProfileCache[targetSellerId] = mockProfile
                Log.d(TAG) { "getSellerProfile: Created mock profile" }
                Result.success(mockProfile)
            }

        } catch (e: Exception) {
            Log.e(TAG) { "getSellerProfile: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun saveSellerProfile(profile: SellerProfile): Result<Unit> {
        return try {
            Log.d(TAG) { "saveSellerProfile: START - ${profile.displayName}" }

            // Convert to map for Firebase
            val profileMap = sellerProfileToMap(profile)

            // Save to GitLive Firebase
            sellersRef.child(profile.id).setValue(profileMap)

            // Update cache
            sellerProfileCache[profile.id] = profile

            Log.d(TAG) { "saveSellerProfile: Success" }
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG) { "saveSellerProfile: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun clearUserData(sellerId: String, buyerProfile: BuyerProfile): Result<CleanUpResult> {
        return try {
            Log.d(TAG) { "clearUserData: START - sellerId=$sellerId, placedOrders=${buyerProfile.placedOrderIds.size}" }

            val now = Clock.System.now().toEpochMilliseconds()
            val futureOrderIds = mutableListOf<String>()
            val cancelledOrders = mutableListOf<String>()
            val skippedOrders = mutableListOf<String>()
            val errors = mutableListOf<String>()

            // Process each order from buyer's placedOrderIds
            // placedOrderIds is Map<String, String> where key=date, value=orderId
            for ((date, orderId) in buyerProfile.placedOrderIds) {
                try {
                    Log.d(TAG) { "clearUserData: Processing order date=$date, orderId=$orderId" }

                    // Load the order from Firebase
                    // Path: seller_profile/{sellerId}/orders/{date}/{orderId}
                    val orderRef = sellersRef.child(sellerId).child("orders").child(date).child(orderId)
                    val snapshot = orderRef.valueEvents.first()

                    if (snapshot.exists) {
                        val orderData = snapshot.value as? Map<*, *>
                        if (orderData != null) {
                            val pickUpDate = (orderData["pickUpDate"] as? Number)?.toLong() ?: 0L
                            val currentStatus = (orderData["status"] as? String) ?: "PLACED"

                            Log.d(TAG) { "clearUserData: Order pickUpDate=$pickUpDate, now=$now, status=$currentStatus" }

                            if (pickUpDate > now) {
                                // Future order - cancel it
                                futureOrderIds.add(orderId)

                                // Only cancel if not already cancelled or completed
                                if (currentStatus != "CANCELLED" && currentStatus != "COMPLETED") {
                                    // Update status to CANCELLED
                                    orderRef.child("status").setValue(OrderStatus.CANCELLED.name)
                                    cancelledOrders.add(orderId)
                                    Log.d(TAG) { "clearUserData: Cancelled order $orderId" }
                                } else {
                                    Log.w(TAG) { "clearUserData: Order $orderId already $currentStatus" }
                                    skippedOrders.add(orderId)
                                }
                            } else {
                                // Past order - keep it for seller records
                                skippedOrders.add(orderId)
                                Log.w(TAG) { "clearUserData: Skipping past order $orderId (pickup was ${pickUpDate})" }
                            }
                        }
                    } else {
                        Log.w(TAG) { "clearUserData: Order $orderId not found" }
                        errors.add("Order $orderId not found")
                    }
                } catch (e: Exception) {
                    Log.e(TAG) { "clearUserData: Error processing order $orderId - ${e.message}" }
                    errors.add("Failed to process order $orderId: ${e.message}")
                }
            }

            // Delete buyer profile
            val profileDeleted = try {
                deleteBuyerProfile(buyerProfile.id)
                true
            } catch (e: Exception) {
                errors.add("Failed to delete profile: ${e.message}")
                false
            }

            // Clear local state
            _buyerProfile.value = null
            sellerProfileCache.remove(sellerId)

            val result = CleanUpResult(
                started = true,
                futureOrderIds = futureOrderIds,
                cancelledOrders = cancelledOrders,
                skippedOrders = skippedOrders,
                profileDeleted = profileDeleted,
                errors = errors
            )

            Log.d(TAG) { "clearUserData: Complete - cancelled=${cancelledOrders.size}, skipped=${skippedOrders.size}, profileDeleted=$profileDeleted" }
            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG) { "clearUserData: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun deleteBuyerProfile(userId: String): Result<Unit> {
        return try {
            Log.d(TAG) { "deleteBuyerProfile: START - userId=$userId" }

            // Delete from Firebase
            buyersRef.child(userId).removeValue()

            // Clear local cache
            _buyerProfile.value = null

            Log.d(TAG) { "deleteBuyerProfile: Success" }
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG) { "deleteBuyerProfile: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun saveDraftBasket(draftBasket: DraftBasket): Result<Unit> {
        return try {
            Log.d(TAG) { "saveDraftBasket: START - ${draftBasket.items.size} items" }

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG) { "saveDraftBasket: No authenticated user" }
                return Result.failure(Exception("User not authenticated"))
            }

            // Save draft basket to Firebase
            val draftBasketMap = draftBasketToMap(draftBasket)
            buyersRef.child(userId).child("draftBasket").setValue(draftBasketMap)

            // Update local cache
            _buyerProfile.value = _buyerProfile.value?.copy(draftBasket = draftBasket)

            Log.d(TAG) { "saveDraftBasket: Success" }
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG) { "saveDraftBasket: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun clearDraftBasket(): Result<Unit> {
        return try {
            Log.d(TAG) { "clearDraftBasket: START" }

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG) { "clearDraftBasket: No authenticated user" }
                return Result.failure(Exception("User not authenticated"))
            }

            // Remove draft basket from Firebase
            buyersRef.child(userId).child("draftBasket").removeValue()

            // Update local cache
            _buyerProfile.value = _buyerProfile.value?.copy(draftBasket = null)

            Log.d(TAG) { "clearDraftBasket: Success" }
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG) { "clearDraftBasket: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    // Helper functions to map Firebase data

    private fun mapSnapshotToBuyerProfile(userId: String, snapshot: DataSnapshot): BuyerProfile {
        // Handle different data types from Firebase
        return when (val value = snapshot.value) {
            is Map<*, *> -> {
                // Parse draft basket if exists
                val draftBasketData = value["draftBasket"] as? Map<*, *>
                val draftBasket = draftBasketData?.let { parseDraftBasket(it) }

                val uuid = value["buyerUUID"] as? String ?: ""
                if (uuid.isNotEmpty()) {
                    pendingTokenStorage?.set(uuid)
                }

                BuyerProfile(
                    id = userId,
                    displayName = value["displayName"] as? String ?: "",
                    emailAddress = value["emailAddress"] as? String ?: "",
                    telephoneNumber = value["telephoneNumber"] as? String ?: "",
                    photoUrl = value["photoUrl"] as? String ?: "",
                    anonymous = value["anonymous"] as? Boolean == true,
                    defaultMarket = value["defaultMarket"] as? String ?: "",
                    defaultPickUpTime = value["defaultPickUpTime"] as? String ?: "",
                    placedOrderIds = (value["placedOrderIds"] as? Map<*, *>)
                        ?.entries?.mapNotNull { (k, v) ->
                            val key = k?.toString() ?: return@mapNotNull null
                            val id = v?.toString() ?: return@mapNotNull null
                            key to id
                        }?.toMap() ?: emptyMap(),
                    favouriteArticles = (value["favouriteArticles"] as? List<*>)
                        ?.mapNotNull { it?.toString() } ?: emptyList(),
                    draftBasket = draftBasket,
                    street = value["street"] as? String ?: "",
                    houseNumber = value["houseNumber"] as? String ?: "",
                    isSelfPickup = value["isSelfPickup"] as? Boolean ?: false
                )
            }
            else -> createDefaultBuyerProfile(userId)
        }
    }

    private fun parseDraftBasket(data: Map<*, *>): DraftBasket {
        val itemsData = data["items"] as? List<*> ?: emptyList<Any>()
        val items = itemsData.mapNotNull { itemData ->
            when (itemData) {
                is Map<*, *> -> OrderedProduct(
                    id = itemData["id"] as? String ?: "",
                    productId = itemData["productId"] as? String ?: "-1",
                    productName = itemData["productName"] as? String ?: "",
                    unit = itemData["unit"] as? String ?: "",
                    price = (itemData["price"] as? Number)?.toDouble() ?: 0.0,
                    amount = itemData["amount"] as? String ?: "",
                    amountCount = (itemData["amountCount"] as? Number)?.toDouble() ?: 0.0,
                    piecesCount = (itemData["piecesCount"] as? Number)?.toInt() ?: -1
                )
                else -> null
            }
        }

        return DraftBasket(
            items = items,
            selectedPickupDate = data["selectedPickupDate"] as? String,
            lastModified = (data["lastModified"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun mapSnapshotToSellerProfile(sellerId: String, snapshot: DataSnapshot): SellerProfile {
        return when (val value = snapshot.value) {
            is Map<*, *> -> {
                // Deserialize markets
                val markets = when (val marketsData = value["markets"]) {
                    is List<*> -> marketsData.mapNotNull { marketData ->
                        when (marketData) {
                            is Map<*, *> -> Market(
                                id = marketData["id"] as? String ?: "",
                                name = marketData["name"] as? String ?: "",
                                street = marketData["street"] as? String ?: "",
                                houseNumber = marketData["houseNumber"] as? String ?: "",
                                city = marketData["city"] as? String ?: "",
                                zipCode = marketData["zipCode"] as? String ?: "",
                                dayOfWeek = marketData["dayOfWeek"] as? String ?: "",
                                begin = marketData["begin"] as? String ?: "",
                                end = marketData["end"] as? String ?: "",
                                dayIndex = (marketData["dayIndex"] as? Number)?.toInt() ?: -1
                            )
                            else -> null
                        }
                    }
                    else -> emptyList()
                }

                SellerProfile(
                    id = sellerId,
                    displayName = value["displayName"] as? String ?: "",
                    firstName = value["firstName"] as? String ?: "",
                    lastName = value["lastName"] as? String ?: "",
                    street = value["street"] as? String ?: "",
                    houseNumber = value["houseNumber"] as? String ?: "",
                    city = value["city"] as? String ?: "",
                    zipCode = value["zipCode"] as? String ?: "",
                    telephoneNumber = value["telephoneNumber"] as? String ?: "",
                    lat = value["lat"] as? String ?: "",
                    lng = value["lng"] as? String ?: "",
                    sellerId = value["sellerId"] as? String ?: sellerId,
                    markets = markets,
                    urls = (value["urls"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                )
            }
            else -> createMockSellerProfile(sellerId)
        }
    }

    /**
     * Parse client IDs from Firebase.
     * Supports both list format and map format ({"buyerId": true}).
     */
    private fun parseClientIds(data: Any?): List<String> {
        return when (data) {
            is List<*> -> data.filterIsInstance<String>()
            is Map<*, *> -> data.keys.filterIsInstance<String>()
            else -> emptyList()
        }
    }

    /**
     * Parse client map from Firebase.
     * Returns uuid → displayName. Legacy `true` values map to empty string.
     */
    private fun parseClientMap(data: Any?): Map<String, String> {
        return when (data) {
            is Map<*, *> -> data.entries.associate { (key, value) ->
                val uuid = key as? String ?: ""
                val name = when (value) {
                    is String -> if (value == "true") "" else value
                    else -> ""
                }
                uuid to name
            }.filterKeys { it.isNotEmpty() }
            is List<*> -> data.filterIsInstance<String>().associateWith { "" }
            else -> emptyMap()
        }
    }

    private fun buyerProfileToMap(profile: BuyerProfile): Map<String, Any?> {
        return mapOf(
            "id" to profile.id,
            "displayName" to profile.displayName,
            "emailAddress" to profile.emailAddress,
            "telephoneNumber" to profile.telephoneNumber,
            "photoUrl" to profile.photoUrl,
            "anonymous" to profile.anonymous,
            "defaultMarket" to profile.defaultMarket,
            "defaultPickUpTime" to profile.defaultPickUpTime,
            "placedOrderIds" to profile.placedOrderIds,
            "favouriteArticles" to profile.favouriteArticles,
            "draftBasket" to profile.draftBasket?.let { draftBasketToMap(it) },
            "street" to profile.street,
            "houseNumber" to profile.houseNumber,
            "isSelfPickup" to profile.isSelfPickup
        )
    }

    private fun draftBasketToMap(draftBasket: DraftBasket): Map<String, Any?> {
        return mapOf(
            "items" to draftBasket.items.map { item ->
                mapOf(
                    "id" to item.id,
                    "productId" to item.productId,
                    "productName" to item.productName,
                    "unit" to item.unit,
                    "price" to item.price,
                    "amount" to item.amount,
                    "amountCount" to item.amountCount,
                    "piecesCount" to item.piecesCount
                )
            },
            "selectedPickupDate" to draftBasket.selectedPickupDate,
            "lastModified" to draftBasket.lastModified
        )
    }

    private fun sellerProfileToMap(profile: SellerProfile): Map<String, Any?> {
        // Serialize markets
        val marketsData = profile.markets.map { market ->
            mapOf(
                "id" to market.id,
                "name" to market.name,
                "street" to market.street,
                "houseNumber" to market.houseNumber,
                "city" to market.city,
                "zipCode" to market.zipCode,
                "dayOfWeek" to market.dayOfWeek,
                "begin" to market.begin,
                "end" to market.end,
                "dayIndex" to market.dayIndex
            )
        }

        return mapOf(
            "id" to profile.id,
            "displayName" to profile.displayName,
            "firstName" to profile.firstName,
            "lastName" to profile.lastName,
            "street" to profile.street,
            "houseNumber" to profile.houseNumber,
            "city" to profile.city,
            "zipCode" to profile.zipCode,
            "telephoneNumber" to profile.telephoneNumber,
            "lat" to profile.lat,
            "lng" to profile.lng,
            "sellerId" to profile.sellerId,
            "markets" to marketsData,
            "urls" to profile.urls
        )
    }

    private fun createDefaultBuyerProfile(userId: String): BuyerProfile {
        return BuyerProfile(
            id = userId,
            displayName = "New User",
            emailAddress = "",
            telephoneNumber = "",
            photoUrl = "",
            anonymous = false,
            defaultMarket = "",
            defaultPickUpTime = "",
            placedOrderIds = emptyMap(),
            favouriteArticles = emptyList()
        )
    }

    // --- buyer side -------------------------------------------------------

    override suspend fun submitAccessRequest(sellerId: String, displayName: String): Result<Unit> {
        return try {
            val uid = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))
            val now = Clock.System.now().toEpochMilliseconds()

            accessRequestsRef.child(sellerId).child(uid).setValue(
                mapOf(
                    "displayName" to displayName,
                    "requestedAt" to now
                )
            )
            buyerAccessStatusRef.child(sellerId).child(uid).setValue(
                mapOf(
                    "status" to AccessStatus.PENDING.name,
                    "displayName" to displayName,
                    "updatedAt" to now
                )
            )
            rememberLinkedSeller(uid, sellerId)

            Log.d(TAG) { "submitAccessRequest: sellerId=$sellerId uid=$uid" }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG) { "submitAccessRequest: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun cancelAccessRequest(sellerId: String): Result<Unit> {
        return try {
            val uid = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))
            accessRequestsRef.child(sellerId).child(uid).removeValue()
            buyerAccessStatusRef.child(sellerId).child(uid).removeValue()
            Log.d(TAG) { "cancelAccessRequest: sellerId=$sellerId" }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG) { "cancelAccessRequest: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun redeemInviteToken(
        sellerId: String,
        token: String,
        displayName: String
    ): Result<Unit> {
        return try {
            val uid = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))
            val now = Clock.System.now().toEpochMilliseconds()

            // Claiming the token is the step the rules allow only while it is
            // unclaimed, so a second redemption - or an expired one - fails here and
            // the access record below is never written.
            inviteTokensRef.child(sellerId).child(token).child("redeemedBy").setValue(uid)

            // The access record cites the token it was granted by; the rules check
            // that token was redeemed by this same uid, so approval is verifiable.
            buyerAccessStatusRef.child(sellerId).child(uid).setValue(
                mapOf(
                    "status" to AccessStatus.APPROVED.name,
                    "displayName" to displayName,
                    "updatedAt" to now,
                    "viaToken" to token
                )
            )
            rememberLinkedSeller(uid, sellerId)

            Log.d(TAG) { "redeemInviteToken: redeemed for sellerId=$sellerId uid=$uid" }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG) { "redeemInviteToken: Failed for sellerId=$sellerId - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun getAccessStatus(sellerId: String): AccessStatus {
        return try {
            val uid = authRepository.getCurrentUserId() ?: return AccessStatus.NONE
            parseStatus(buyerAccessStatusRef.child(sellerId).child(uid).valueEvents.first())
        } catch (e: Exception) {
            Log.w(TAG) { "getAccessStatus: sellerId=$sellerId - ${e.message}" }
            AccessStatus.NONE
        }
    }

    override fun observeAccessStatus(sellerId: String): Flow<AccessStatus> = flow {
        val uid = authRepository.getCurrentUserId()
        if (uid == null) {
            emit(AccessStatus.NONE)
            return@flow
        }
        emitAll(
            buyerAccessStatusRef.child(sellerId).child(uid).valueEvents
                .map { parseStatus(it) }
                .catch { e ->
                    Log.w(TAG) { "observeAccessStatus: sellerId=$sellerId - ${e.message}" }
                    emit(AccessStatus.NONE)
                }
        )
    }

    override suspend fun updateOwnDisplayName(sellerId: String, displayName: String): Result<Unit> {
        return try {
            val uid = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))
            buyerAccessStatusRef.child(sellerId).child(uid).child("displayName").setValue(displayName)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG) { "updateOwnDisplayName: sellerId=$sellerId - ${e.message}" }
            Result.failure(e)
        }
    }

    // --- seller side ------------------------------------------------------

    override fun observeAccessRequests(sellerId: String): Flow<List<AccessRequest>> {
        return accessRequestsRef.child(sellerId).valueEvents.map { snapshot ->
            if (!snapshot.exists) return@map emptyList()
            snapshot.children.mapNotNull { child ->
                val buyerId = child.key ?: return@mapNotNull null
                val data = child.value as? Map<*, *> ?: return@mapNotNull null
                AccessRequest(
                    sellerId = sellerId,
                    buyerId = buyerId,
                    buyerDisplayName = data["displayName"] as? String ?: "",
                    requestedAt = (data["requestedAt"] as? Number)?.toLong() ?: 0L
                )
            }
        }
    }

    override fun observeBuyers(sellerId: String): Flow<List<BuyerAccess>> {
        return buyerAccessStatusRef.child(sellerId).valueEvents.map { snapshot ->
            if (!snapshot.exists) return@map emptyList()
            snapshot.children.mapNotNull { child ->
                val buyerId = child.key ?: return@mapNotNull null
                val data = child.value as? Map<*, *> ?: return@mapNotNull null
                val statusName = data["status"] as? String ?: return@mapNotNull null
                val status = AccessStatus.entries.firstOrNull { it.name == statusName }
                    ?: return@mapNotNull null
                BuyerAccess(
                    buyerId = buyerId,
                    displayName = data["displayName"] as? String ?: "",
                    status = status,
                    updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                )
            }
        }
    }

    override suspend fun approveAccessRequest(
        sellerId: String,
        buyerId: String,
        displayName: String
    ): Result<Unit> {
        return try {
            buyerAccessStatusRef.child(sellerId).child(buyerId).updateChildren(
                mapOf(
                    "status" to AccessStatus.APPROVED.name,
                    "displayName" to displayName,
                    "updatedAt" to Clock.System.now().toEpochMilliseconds()
                )
            )
            accessRequestsRef.child(sellerId).child(buyerId).removeValue()
            Log.d(TAG) { "approveAccessRequest: approved buyerId=$buyerId" }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG) { "approveAccessRequest: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun blockBuyer(sellerId: String, buyerId: String): Result<Unit> {
        return try {
            buyerAccessStatusRef.child(sellerId).child(buyerId).updateChildren(
                mapOf(
                    "status" to AccessStatus.BLOCKED.name,
                    "updatedAt" to Clock.System.now().toEpochMilliseconds()
                )
            )
            accessRequestsRef.child(sellerId).child(buyerId).removeValue()
            Log.d(TAG) { "blockBuyer: blocked buyerId=$buyerId" }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG) { "blockBuyer: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun unblockBuyer(sellerId: String, buyerId: String): Result<Unit> {
        return try {
            buyerAccessStatusRef.child(sellerId).child(buyerId).updateChildren(
                mapOf(
                    "status" to AccessStatus.APPROVED.name,
                    "updatedAt" to Clock.System.now().toEpochMilliseconds()
                )
            )
            Log.d(TAG) { "unblockBuyer: unblocked buyerId=$buyerId" }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG) { "unblockBuyer: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createInviteToken(
        sellerId: String,
        displayNameHint: String,
        ttlMillis: Long,
        token: String?
    ): Result<String> {
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            val id = token ?: Uuid.random().toString()
            inviteTokensRef.child(sellerId).child(id).setValue(
                mapOf(
                    "createdAt" to now,
                    "expiresAt" to now + ttlMillis,
                    "displayNameHint" to displayNameHint
                )
            )
            Log.d(TAG) { "createInviteToken: minted a token for sellerId=$sellerId" }
            Result.success(id)
        } catch (e: Exception) {
            Log.e(TAG) { "createInviteToken: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    // --- helpers ----------------------------------------------------------

    private fun parseStatus(snapshot: DataSnapshot): AccessStatus {
        if (!snapshot.exists) return AccessStatus.NONE
        val data = snapshot.value as? Map<*, *> ?: return AccessStatus.NONE
        val statusName = data["status"] as? String ?: return AccessStatus.NONE
        return AccessStatus.entries.firstOrNull { it.name == statusName } ?: AccessStatus.NONE
    }

    /**
     * Note which sellers this buyer has an access record with, so a later rename can
     * be pushed to each of them. Best effort: losing a link only means a stale name.
     */
    private suspend fun rememberLinkedSeller(uid: String, sellerId: String) {
        try {
            buyersRef.child(uid).child("linkedSellerIds").child(sellerId).setValue(true)
        } catch (e: Exception) {
            Log.w(TAG) { "rememberLinkedSeller: sellerId=$sellerId - ${e.message}" }
        }
    }

    /**
     * Push a buyer's new display name into every seller access record they hold.
     *
     * buyer_access_status is the only buyer-writable node a seller can read, so this
     * is the sole path by which a rename reaches the seller side today. The
     * seller_events sync channel is meant to replace it.
     */
    private suspend fun syncDisplayNameToLinkedSellers(userId: String, displayName: String) {
        try {
            val linksSnapshot = buyersRef.child(userId).child("linkedSellerIds").valueEvents.first()
            val sellerIds = (linksSnapshot.value as? Map<*, *>)?.keys?.filterIsInstance<String>() ?: return
            for (sellerId in sellerIds) {
                try {
                    buyerAccessStatusRef.child(sellerId).child(userId)
                        .child("displayName").setValue(displayName)
                } catch (e: Exception) {
                    // A blocked seller relationship rejects this write - expected, and
                    // it must not stop the name reaching the buyer's other sellers.
                    Log.w(TAG) { "syncDisplayNameToLinkedSellers: sellerId=$sellerId - ${e.message}" }
                }
            }
            Log.d(TAG) { "syncDisplayNameToLinkedSellers: pushed name to ${sellerIds.size} seller(s)" }
        } catch (e: Exception) {
            Log.e(TAG) { "syncDisplayNameToLinkedSellers: Error - ${e.message}" }
        }
    }
    private fun createMockSellerProfile(sellerId: String): SellerProfile {
        return SellerProfile(
            id = sellerId,
            displayName = when (sellerId) {
                "seller_001" -> "Test Store"
                "seller_002" -> "Demo Shop"
                else -> "GitLive Seller"
            },
            firstName = "Test",
            lastName = "Seller",
            street = "Main Street",
            houseNumber = "123",
            city = "Test City",
            zipCode = "12345",
            telephoneNumber = "+49123456789",
            lat = "52.520008",
            lng = "13.404954",
            sellerId = sellerId,
            markets = emptyList(),
            urls = emptyList()
        )
    }
}