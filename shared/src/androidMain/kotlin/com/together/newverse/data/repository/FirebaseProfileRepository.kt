package com.together.newverse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
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
    private var profileListener: ValueEventListener? = null

    init {
        Database.initialize()
        // Start observing buyer profile changes in real-time
        startObservingBuyerProfile()
    }

    /**
     * Start real-time observation of buyer profile changes
     */
    private fun startObservingBuyerProfile() {
        profileListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dto = snapshot.getValue(BuyerProfileDto::class.java)

                // Get current user from Firebase Auth
                val currentUser = FirebaseAuth.getInstance().currentUser
                val userId = currentUser?.uid ?: ""
                val authPhotoUrl = currentUser?.photoUrl?.toString() ?: ""
                val authDisplayName = currentUser?.displayName ?: ""
                val authEmail = currentUser?.email ?: ""

                if (dto != null) {
                    val profile = dto.toDomain().copy(
                        id = userId,
                        photoUrl = dto.photoUrl.ifEmpty { authPhotoUrl },
                        displayName = dto.displayName.ifEmpty { authDisplayName },
                        emailAddress = dto.emailAddress.ifEmpty { authEmail }
                    )
                    println("🔥 FirebaseProfileRepository: Profile updated via listener - ${profile.placedOrderIds.size} orders, ${profile.favouriteArticles.size} favourites")
                    _buyerProfile.value = profile
                } else {
                    println("⚠️ FirebaseProfileRepository: Profile DTO is null from listener!")
                    _buyerProfile.value = null
                }
            }

            override fun onCancelled(error: DatabaseError) {
                println("❌ FirebaseProfileRepository: Listener cancelled - ${error.message}")
            }
        }

        Database.buyer().addValueEventListener(profileListener!!)
    }

    override fun observeBuyerProfile(): Flow<BuyerProfile?> {
        return _buyerProfile.asStateFlow()
    }

    override suspend fun getBuyerProfile(): Result<BuyerProfile> {
        return try {
            println("🔥 FirebaseProfileRepository.getBuyerProfile: START")

            val snapshot = Database.buyer().getSingleValue()
            val dto = snapshot.getValue(BuyerProfileDto::class.java)

            // Get current user from Firebase Auth
            val currentUser = FirebaseAuth.getInstance().currentUser
            val userId = currentUser?.uid ?: ""
            val authPhotoUrl = currentUser?.photoUrl?.toString() ?: ""
            val authDisplayName = currentUser?.displayName ?: ""
            val authEmail = currentUser?.email ?: ""

            println("🔥 FirebaseProfileRepository.getBuyerProfile: Auth user - photoUrl=$authPhotoUrl, displayName=$authDisplayName")

            if (dto != null) {
                // Merge Firebase Auth data with profile data
                val profile = dto.toDomain().copy(
                    id = userId,
                    // Use Firebase Auth photo if available, otherwise use what's in the database
                    photoUrl = dto.photoUrl.ifEmpty { authPhotoUrl },
                    // Use Firebase Auth displayName if profile doesn't have one
                    displayName = dto.displayName.ifEmpty { authDisplayName },
                    // Use Firebase Auth email if profile doesn't have one
                    emailAddress = dto.emailAddress.ifEmpty { authEmail }
                )
                _buyerProfile.value = profile
                println("✅ FirebaseProfileRepository.getBuyerProfile: Success - ${profile.displayName}, photoUrl=${profile.photoUrl}, ${profile.placedOrderIds.size} orders")
                Result.success(profile)
            } else {
                println("⚠️ FirebaseProfileRepository.getBuyerProfile: No profile found, creating default from Auth")
                // Create a default profile from Firebase Auth data
                val defaultProfile = BuyerProfile(
                    id = userId,
                    displayName = authDisplayName,
                    emailAddress = authEmail,
                    photoUrl = authPhotoUrl,
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
