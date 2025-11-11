package com.together.newverse.data.repository

import com.together.newverse.data.firebase.Database
import com.together.newverse.data.firebase.getSingleValue
import com.together.newverse.data.firebase.model.BuyerProfileDto
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase implementation of ProfileRepository
 */
class FirebaseProfileRepository : ProfileRepository {

    private val _buyerProfile = MutableStateFlow<BuyerProfile?>(null)

    init {
        Database.initialize()
    }

    override fun observeBuyerProfile(): Flow<BuyerProfile?> {
        return _buyerProfile.asStateFlow()
    }

    override suspend fun getBuyerProfile(): Result<BuyerProfile> {
        return try {
            println("🔥 FirebaseProfileRepository.getBuyerProfile: START")

            val snapshot = Database.buyer().getSingleValue()
            val dto = snapshot.getValue(BuyerProfileDto::class.java)

            // Get current user ID from Firebase Auth
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

            if (dto != null) {
                val profile = dto.toDomain().copy(id = userId)
                _buyerProfile.value = profile
                println("✅ FirebaseProfileRepository.getBuyerProfile: Success - ${profile.displayName}, ${profile.placedOrderIds.size} orders")
                Result.success(profile)
            } else {
                println("⚠️ FirebaseProfileRepository.getBuyerProfile: No profile found, creating default")
                // Create a default empty profile
                val defaultProfile = BuyerProfile(
                    id = userId,
                    displayName = "",
                    emailAddress = "",
                    placedOrderIds = emptyMap()
                )
                Result.success(defaultProfile)
            }
        } catch (e: Exception) {
            println("❌ FirebaseProfileRepository.getBuyerProfile: Error - ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun saveBuyerProfile(profile: BuyerProfile): Result<BuyerProfile> {
        return try {
            println("🔥 FirebaseProfileRepository.saveBuyerProfile: START - ${profile.displayName}")

            val dto = BuyerProfileDto.fromDomain(profile)
            Database.buyer().setValue(dto).await()

            _buyerProfile.value = profile
            println("✅ FirebaseProfileRepository.saveBuyerProfile: Success")
            Result.success(profile)
        } catch (e: Exception) {
            println("❌ FirebaseProfileRepository.saveBuyerProfile: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getSellerProfile(sellerId: String): Result<SellerProfile> {
        return try {
            // TODO: Implement Firebase seller profile fetching
            println("⚠️ FirebaseProfileRepository.getSellerProfile: Not yet implemented")
            Result.failure(NotImplementedError("Seller profile fetching not yet implemented"))
        } catch (e: Exception) {
            println("❌ FirebaseProfileRepository.getSellerProfile: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun saveSellerProfile(profile: SellerProfile): Result<Unit> {
        return try {
            // TODO: Implement Firebase seller profile saving
            println("⚠️ FirebaseProfileRepository.saveSellerProfile: Not yet implemented")
            Result.failure(NotImplementedError("Seller profile saving not yet implemented"))
        } catch (e: Exception) {
            println("❌ FirebaseProfileRepository.saveSellerProfile: Error - ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun clearUserData(sellerId: String, buyerProfile: BuyerProfile): Result<Boolean> {
        return try {
            println("🔥 FirebaseProfileRepository.clearUserData: START")
            _buyerProfile.value = null
            println("✅ FirebaseProfileRepository.clearUserData: Success")
            Result.success(true)
        } catch (e: Exception) {
            println("❌ FirebaseProfileRepository.clearUserData: Error - ${e.message}")
            Result.failure(e)
        }
    }
}
