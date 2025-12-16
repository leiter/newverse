package com.together.newverse.domain.repository

import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.SellerProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing buyer and seller profiles
 */
interface ProfileRepository {
    /**
     * Observe buyer profile
     * @return Flow of buyer profile with real-time updates
     */
    fun observeBuyerProfile(): Flow<BuyerProfile?>

    /**
     * Get buyer profile
     * @return Buyer profile or null if not exists
     */
    suspend fun getBuyerProfile(): Result<BuyerProfile>

    /**
     * Save buyer profile
     * @param profile The profile to save
     * @return Saved profile
     */
    suspend fun saveBuyerProfile(profile: BuyerProfile): Result<BuyerProfile>

    /**
     * Get seller profile
     * @param sellerId The seller's ID, if empty gets first seller
     * @return Seller profile
     */
    suspend fun getSellerProfile(sellerId: String = ""): Result<SellerProfile>

    /**
     * Save seller profile
     * @param profile The profile to save
     * @return Success or failure result
     */
    suspend fun saveSellerProfile(profile: SellerProfile): Result<Unit>

    /**
     * Clear user data
     * @param sellerId The seller's ID
     * @param buyerProfile The buyer profile to clear
     * @return Cleanup result with details
     */
    suspend fun clearUserData(sellerId: String, buyerProfile: BuyerProfile): Result<Boolean>

    /**
     * Delete buyer profile for a specific user
     * Used when guest user logs out to clean up their data
     * @param userId The user ID whose profile should be deleted
     * @return Success or failure result
     */
    suspend fun deleteBuyerProfile(userId: String): Result<Unit>
}
