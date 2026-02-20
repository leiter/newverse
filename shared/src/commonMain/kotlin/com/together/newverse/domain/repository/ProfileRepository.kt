package com.together.newverse.domain.repository

import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.CleanUpResult
import com.together.newverse.domain.model.DraftBasket
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
     * Clear user data when account is deleted.
     * - Future orders (pickup date > now) are CANCELLED
     * - Past orders are kept for seller records
     * - Buyer profile is deleted
     *
     * @param sellerId The seller's ID
     * @param buyerProfile The buyer profile to clear
     * @return CleanUpResult with details about what was cancelled/kept
     */
    suspend fun clearUserData(sellerId: String, buyerProfile: BuyerProfile): Result<CleanUpResult>

    /**
     * Delete buyer profile for a specific user
     * Used when guest user logs out to clean up their data
     * @param userId The user ID whose profile should be deleted
     * @return Success or failure result
     */
    suspend fun deleteBuyerProfile(userId: String): Result<Unit>

    /**
     * Add a buyer as a known client to a seller's profile.
     * @param sellerId The seller's ID
     * @param buyerId The buyer's ID to register
     */
    suspend fun addKnownClient(sellerId: String, buyerId: String): Result<Unit>

    /**
     * Block a buyer from placing orders with a seller.
     * Moves from knownClientIds to blockedClientIds.
     * @param sellerId The seller's ID
     * @param buyerId The buyer's ID to block
     */
    suspend fun blockClient(sellerId: String, buyerId: String): Result<Unit>

    /**
     * Unblock a buyer, allowing them to place orders again.
     * Moves from blockedClientIds to knownClientIds.
     * @param sellerId The seller's ID
     * @param buyerId The buyer's ID to unblock
     */
    suspend fun unblockClient(sellerId: String, buyerId: String): Result<Unit>

    /**
     * Check if a buyer is blocked by a seller.
     * @param sellerId The seller's ID
     * @param buyerId The buyer's ID to check
     * @return true if blocked
     */
    suspend fun isClientBlocked(sellerId: String, buyerId: String): Boolean

    /**
     * Save draft basket to buyer profile
     * @param draftBasket The draft basket to save
     * @return Success or failure result
     */
    suspend fun saveDraftBasket(draftBasket: DraftBasket): Result<Unit>

    /**
     * Clear draft basket from buyer profile
     * Called when an order is placed
     * @return Success or failure result
     */
    suspend fun clearDraftBasket(): Result<Unit>
}
