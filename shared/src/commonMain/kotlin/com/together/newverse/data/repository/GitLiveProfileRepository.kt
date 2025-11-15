package com.together.newverse.data.repository

import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import dev.gitlive.firebase.database.DatabaseReference
import dev.gitlive.firebase.database.DataSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
    private val sellersRef = database.reference("seller_profiles")

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

    override suspend fun clearUserData(sellerId: String, buyerProfile: BuyerProfile): Result<Boolean> {
        return try {
            println("🔐 GitLiveProfileRepository.clearUserData: START")

            // Clear local state
            _buyerProfile.value = null
            sellerProfileCache.remove(sellerId)

            println("✅ GitLiveProfileRepository.clearUserData: Success")
            Result.success(true)

        } catch (e: Exception) {
            println("❌ GitLiveProfileRepository.clearUserData: Error - ${e.message}")
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
                    anonymous = value["anonymous"] as? Boolean ?: false,
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
                    markets = emptyList(), // Markets would need custom deserialization
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