package com.together.newverse.domain.repository

import com.together.newverse.domain.model.AccessRequest
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.BuyerAccess
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
     * Get only the seller's public display name.
     *
     * Buyers cannot read the whole seller profile - it holds the seller's client
     * lists - so this reads the single public child instead.
     */
    suspend fun getSellerDisplayName(sellerId: String): Result<String>

    /**
     * Drop every cached profile.
     *
     * Must be called on sign-out: the caches outlive the session, so without
     * this the next user is served the previous one's profile until Firebase
     * answers.
     */
    suspend fun clearCache() {}

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

    // --- buyer side -------------------------------------------------------
    // Every access record is keyed by the buyer's own auth uid, so these take no
    // buyer identifier: the repository reads it from the auth session.

    /**
     * Submit an access request to a seller.
     * Writes access_requests/{sellerId}/{uid} and a PENDING buyer_access_status record.
     */
    suspend fun submitAccessRequest(sellerId: String, displayName: String): Result<Unit>

    /**
     * Withdraw a previously submitted access request, clearing both nodes.
     */
    suspend fun cancelAccessRequest(sellerId: String): Result<Unit>

    /**
     * Redeem a seller's invite token, granting immediate approval.
     *
     * Marks the token as redeemed by this buyer and writes an APPROVED
     * buyer_access_status record citing it. The rules verify the cited token was
     * redeemed by this same uid, so the approval is provable rather than asserted;
     * a token that is already redeemed, expired or unknown fails.
     */
    suspend fun redeemInviteToken(sellerId: String, token: String, displayName: String): Result<Unit>

    /**
     * One-shot read of this buyer's access status for a seller.
     */
    suspend fun getAccessStatus(sellerId: String): AccessStatus

    /**
     * Observe this buyer's access status for a seller.
     */
    fun observeAccessStatus(sellerId: String): Flow<AccessStatus>

    /**
     * Push this buyer's current display name into their access record, so the
     * seller's customer list reflects a rename.
     *
     * Interim mechanism: the buyer writing into a seller-read node is what the
     * seller_events sync channel is meant to replace.
     */
    suspend fun updateOwnDisplayName(sellerId: String, displayName: String): Result<Unit>

    // --- seller side ------------------------------------------------------

    /**
     * Observe all pending access requests for a seller.
     */
    fun observeAccessRequests(sellerId: String): Flow<List<AccessRequest>>

    /**
     * Observe every buyer this seller has an access record for, whatever its status.
     * Replaces the approvedBuyerIds / blockedClientIds maps.
     */
    fun observeBuyers(sellerId: String): Flow<List<BuyerAccess>>

    /**
     * Approve a buyer's request and clear it from the pending list.
     */
    suspend fun approveAccessRequest(sellerId: String, buyerId: String, displayName: String): Result<Unit>

    /**
     * Block a buyer, clearing any pending request. A blocked buyer cannot order,
     * cannot reset their own status, and cannot append to the event log.
     */
    suspend fun blockBuyer(sellerId: String, buyerId: String): Result<Unit>

    /**
     * Return a blocked buyer to APPROVED.
     */
    suspend fun unblockBuyer(sellerId: String, buyerId: String): Result<Unit>

    /**
     * Mint an invite token for a buyer who does not exist yet, for a QR code or link.
     * @return the token, to embed in the link.
     */
    suspend fun createInviteToken(
        sellerId: String,
        displayNameHint: String,
        ttlMillis: Long,
        token: String? = null
    ): Result<String>
}
