package com.together.newverse.domain.repository

import com.together.newverse.domain.model.AccessRequest
import com.together.newverse.domain.model.AccessStatus
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

    /**
     * Persist buyerUUID to the buyer's Firebase profile so security rules can verify ownership.
     * Must be called before observeAccessStatus to ensure the read is permitted.
     */
    suspend fun saveBuyerUUID(uuid: String): Result<Unit>

    /**
     * Submit an access request from a buyer to a seller.
     * Writes to access_requests/{sellerId}/{buyerUUID} and sets status to PENDING.
     */
    suspend fun submitAccessRequest(sellerId: String, buyerUUID: String, displayName: String): Result<Unit>

    /**
     * Cancel a previously submitted access request.
     * Removes access_requests/{sellerId}/{buyerUUID} and buyer_access_status/{sellerId}/{buyerUUID}.
     */
    suspend fun cancelAccessRequest(sellerId: String, buyerUUID: String): Result<Unit>

    /**
     * One-shot read of the buyer's access status for a specific seller.
     */
    suspend fun getAccessStatus(buyerUUID: String, sellerId: String): AccessStatus

    /**
     * Observe real-time access status updates for a buyer/seller pair.
     */
    fun observeAccessStatus(buyerUUID: String, sellerId: String): Flow<AccessStatus>

    /**
     * Observe all pending access requests for a seller.
     */
    fun observeAccessRequests(sellerId: String): Flow<List<AccessRequest>>

    /**
     * Approve an access request — sets status to APPROVED and removes the request.
     */
    suspend fun approveAccessRequest(sellerId: String, buyerUUID: String): Result<Unit>

    /**
     * Block a buyer — sets status to BLOCKED and removes the request.
     */
    suspend fun blockBuyer(sellerId: String, buyerUUID: String): Result<Unit>

    /**
     * Approve an access request and track the buyer in the seller's approvedBuyerIds list.
     */
    suspend fun approveAccessRequestWithTracking(sellerId: String, buyerUUID: String, displayName: String): Result<Unit>

    /**
     * Unblock a previously approved buyer — sets status back to APPROVED and moves them
     * from blockedClientIds back to approvedBuyerIds.
     */
    suspend fun unblockApprovedBuyer(sellerId: String, buyerUUID: String): Result<Unit>

    /**
     * Update the display name of an approved buyer in both buyer_access_status and approvedBuyerIds.
     * Used when a QR-pre-approved buyer connects and their real name becomes available.
     */
    suspend fun updateApprovedBuyerDisplayName(sellerId: String, buyerUUID: String, displayName: String): Result<Unit>

    /**
     * Observe the approved buyer IDs for a seller in real-time.
     * Emits a map of buyerUUID to displayName. Empty string means name is unknown.
     */
    fun observeApprovedBuyerIds(sellerId: String): Flow<Map<String, String>>

    /**
     * Resolve the display name for a buyer identified by their UUID token.
     * Reads authUID from buyer_access_status/{sellerId}/{buyerUUID}/authUID,
     * then fetches the display name from buyer_profile/{authUID}/displayName.
     * Returns empty string if the buyer has not connected yet (knowledge gap).
     */
    suspend fun getBuyerDisplayName(sellerId: String, buyerUUID: String): String
}
