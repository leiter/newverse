package com.together.newverse.data.repository

import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.CleanUpResult
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.ProfileRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.DataSnapshot
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

/**
 * GitLive implementation of ProfileRepository for cross-platform profile management.
 * This version uses the correct GitLive Firebase SDK APIs.
 */
class GitLiveProfileRepository(
    private val authRepository: AuthRepository
) : ProfileRepository {

    // GitLive Firebase Database references
    private val database = Firebase.database
    private val buyersRef = database.reference("buyer_profile")
    private val sellersRef = database.reference("seller_profile")

    // Local cache for performance
    private val _buyerProfile = MutableStateFlow<BuyerProfile?>(null)
    private val sellerProfileCache = mutableMapOf<String, SellerProfile>()

    override fun observeBuyerProfile(): Flow<BuyerProfile?> {
        println("🔐 GitLiveProfileRepository.observeBuyerProfile: Setting up profile observer")
        return _buyerProfile.asStateFlow()
    }

    override suspend fun getBuyerProfile(): Result<BuyerProfile> {
        return try {
            println("🔐 GitLiveProfileRepository.getBuyerProfile: START")

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                println("❌ GitLiveProfileRepository.getBuyerProfile: No authenticated user")
                return Result.failure(Exception("User not authenticated"))
            }

            // Check cache first
            val cachedProfile = _buyerProfile.value
            if (cachedProfile != null) {
                println("✅ GitLiveProfileRepository.getBuyerProfile: Returning cached profile")
                return Result.success(cachedProfile)
            }

            // Fetch from GitLive Firebase using valueEvents Flow
            val snapshot = buyersRef.child(userId).valueEvents.first()

            // Check if data exists
            if (snapshot.exists) {
                val profile = mapSnapshotToBuyerProfile(userId, snapshot)
                _buyerProfile.value = profile
                println("✅ GitLiveProfileRepository.getBuyerProfile: Fetched from Firebase")
                Result.success(profile)
            } else {
                // Create default profile for new users
                val defaultProfile = createDefaultBuyerProfile(userId)
                _buyerProfile.value = defaultProfile
                println("✅ GitLiveProfileRepository.getBuyerProfile: Created default profile")
                Result.success(defaultProfile)
            }

        } catch (e: Exception) {
            println("❌ GitLiveProfileRepository.getBuyerProfile: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun saveBuyerProfile(profile: BuyerProfile): Result<BuyerProfile> {
        return try {
            println("🔐 GitLiveProfileRepository.saveBuyerProfile: START - ${profile.displayName}")

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                println("❌ GitLiveProfileRepository.saveBuyerProfile: No authenticated user")
                return Result.failure(Exception("User not authenticated"))
            }

            // Ensure profile ID matches authenticated user
            val profileWithCorrectId = profile.copy(id = userId)

            // Convert to map for Firebase
            val profileMap = buyerProfileToMap(profileWithCorrectId)

            // Save to GitLive Firebase
            buyersRef.child(userId).setValue(profileMap)

            // Update local cache
            _buyerProfile.value = profileWithCorrectId

            println("✅ GitLiveProfileRepository.saveBuyerProfile: Success")
            Result.success(profileWithCorrectId)

        } catch (e: Exception) {
            println("❌ GitLiveProfileRepository.saveBuyerProfile: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getSellerProfile(sellerId: String): Result<SellerProfile> {
        return try {
            println("🔐 GitLiveProfileRepository.getSellerProfile: START - sellerId=$sellerId")

            // Check cache first
            if (sellerId.isNotEmpty() && sellerProfileCache.containsKey(sellerId)) {
                println("✅ GitLiveProfileRepository.getSellerProfile: Returning cached profile")
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
                println("✅ GitLiveProfileRepository.getSellerProfile: Fetched from Firebase")
                Result.success(profile)
            } else {
                // Return mock for testing if no seller found
                val mockProfile = createMockSellerProfile(targetSellerId)
                sellerProfileCache[targetSellerId] = mockProfile
                println("✅ GitLiveProfileRepository.getSellerProfile: Created mock profile")
                Result.success(mockProfile)
            }

        } catch (e: Exception) {
            println("❌ GitLiveProfileRepository.getSellerProfile: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun saveSellerProfile(profile: SellerProfile): Result<Unit> {
        return try {
            println("🔐 GitLiveProfileRepository.saveSellerProfile: START - ${profile.displayName}")

            // Convert to map for Firebase
            val profileMap = sellerProfileToMap(profile)

            // Save to GitLive Firebase
            sellersRef.child(profile.id).setValue(profileMap)

            // Update cache
            sellerProfileCache[profile.id] = profile

            println("✅ GitLiveProfileRepository.saveSellerProfile: Success")
            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ GitLiveProfileRepository.saveSellerProfile: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun clearUserData(sellerId: String, buyerProfile: BuyerProfile): Result<CleanUpResult> {
        return try {
            println("🔐 GitLiveProfileRepository.clearUserData: START - sellerId=$sellerId, placedOrders=${buyerProfile.placedOrderIds.size}")

            val now = Clock.System.now().toEpochMilliseconds()
            val futureOrderIds = mutableListOf<String>()
            val cancelledOrders = mutableListOf<String>()
            val skippedOrders = mutableListOf<String>()
            val errors = mutableListOf<String>()

            // Process each order from buyer's placedOrderIds
            // placedOrderIds is Map<String, String> where key=date, value=orderId
            for ((date, orderId) in buyerProfile.placedOrderIds) {
                try {
                    println("🔐 GitLiveProfileRepository.clearUserData: Processing order date=$date, orderId=$orderId")

                    // Load the order from Firebase
                    // Path: seller_profile/{sellerId}/orders/{date}/{orderId}
                    val orderRef = sellersRef.child(sellerId).child("orders").child(date).child(orderId)
                    val snapshot = orderRef.valueEvents.first()

                    if (snapshot.exists) {
                        val orderData = snapshot.value as? Map<*, *>
                        if (orderData != null) {
                            val pickUpDate = (orderData["pickUpDate"] as? Number)?.toLong() ?: 0L
                            val currentStatus = (orderData["status"] as? String) ?: "PLACED"

                            println("🔐 GitLiveProfileRepository.clearUserData: Order pickUpDate=$pickUpDate, now=$now, status=$currentStatus")

                            if (pickUpDate > now) {
                                // Future order - cancel it
                                futureOrderIds.add(orderId)

                                // Only cancel if not already cancelled or completed
                                if (currentStatus != "CANCELLED" && currentStatus != "COMPLETED") {
                                    // Update status to CANCELLED
                                    orderRef.child("status").setValue(OrderStatus.CANCELLED.name)
                                    cancelledOrders.add(orderId)
                                    println("✅ GitLiveProfileRepository.clearUserData: Cancelled order $orderId")
                                } else {
                                    println("⏭️ GitLiveProfileRepository.clearUserData: Order $orderId already $currentStatus")
                                    skippedOrders.add(orderId)
                                }
                            } else {
                                // Past order - keep it for seller records
                                skippedOrders.add(orderId)
                                println("⏭️ GitLiveProfileRepository.clearUserData: Skipping past order $orderId (pickup was ${pickUpDate})")
                            }
                        }
                    } else {
                        println("⚠️ GitLiveProfileRepository.clearUserData: Order $orderId not found")
                        errors.add("Order $orderId not found")
                    }
                } catch (e: Exception) {
                    println("❌ GitLiveProfileRepository.clearUserData: Error processing order $orderId - ${e.message}")
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

            println("✅ GitLiveProfileRepository.clearUserData: Complete - cancelled=${cancelledOrders.size}, skipped=${skippedOrders.size}, profileDeleted=$profileDeleted")
            Result.success(result)

        } catch (e: Exception) {
            println("❌ GitLiveProfileRepository.clearUserData: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteBuyerProfile(userId: String): Result<Unit> {
        return try {
            println("🔐 GitLiveProfileRepository.deleteBuyerProfile: START - userId=$userId")

            // Delete from Firebase
            buyersRef.child(userId).removeValue()

            // Clear local cache
            _buyerProfile.value = null

            println("✅ GitLiveProfileRepository.deleteBuyerProfile: Success")
            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ GitLiveProfileRepository.deleteBuyerProfile: Error - ${e.message}")
            Result.failure(e)
        }
    }

    // Helper functions to map Firebase data

    private fun mapSnapshotToBuyerProfile(userId: String, snapshot: DataSnapshot): BuyerProfile {
        val value = snapshot.value

        // Handle different data types from Firebase
        return when (value) {
            is Map<*, *> -> {
                BuyerProfile(
                    id = userId,
                    displayName = value["displayName"] as? String ?: "",
                    emailAddress = value["emailAddress"] as? String ?: "",
                    telephoneNumber = value["telephoneNumber"] as? String ?: "",
                    photoUrl = value["photoUrl"] as? String ?: "",
                    anonymous = value["anonymous"] as? Boolean == true,
                    defaultMarket = value["defaultMarket"] as? String ?: "",
                    defaultPickUpTime = value["defaultPickUpTime"] as? String ?: "",
                    placedOrderIds = (value["placedOrderIds"] as? Map<String, String>) ?: emptyMap(),
                    favouriteArticles = (value["favouriteArticles"] as? List<String>) ?: emptyList()
                )
            }
            else -> createDefaultBuyerProfile(userId)
        }
    }

    private fun mapSnapshotToSellerProfile(sellerId: String, snapshot: DataSnapshot): SellerProfile {
        val value = snapshot.value

        return when (value) {
            is Map<*, *> -> {
                // Deserialize markets
                val marketsData = value["markets"]
                val markets = when (marketsData) {
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
                    urls = (value["urls"] as? List<String>) ?: emptyList(),
                    knownClientIds = (value["knownClientIds"] as? List<String>) ?: emptyList()
                )
            }
            else -> createMockSellerProfile(sellerId)
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
            "favouriteArticles" to profile.favouriteArticles
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
            "urls" to profile.urls,
            "knownClientIds" to profile.knownClientIds
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
            urls = emptyList(),
            knownClientIds = emptyList()
        )
    }
}