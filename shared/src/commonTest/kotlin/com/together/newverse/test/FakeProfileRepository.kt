package com.together.newverse.test

import com.together.newverse.domain.model.AccessRequest
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.BuyerAccess
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.CleanUpResult
import com.together.newverse.domain.model.DraftBasket
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fake implementation of ProfileRepository for testing.
 * Allows controlling profile data and tracking operations.
 */
class FakeProfileRepository : ProfileRepository {

    private val _buyerProfile = MutableStateFlow<BuyerProfile?>(null)
    private val _sellerProfile = MutableStateFlow<SellerProfile?>(null)

    // Track operations for verification
    var saveSellerProfileCalled = false
        private set
    var lastSavedSellerProfile: SellerProfile? = null
        private set

    // Configuration for test scenarios
    var shouldFailGetSellerProfile = false
    var shouldFailSaveSellerProfile = false
    var shouldFailGetBuyerProfile = false
    var shouldFailSaveBuyerProfile = false
    var failureMessage = "Test error"

    // Track buyer profile operations
    var saveBuyerProfileCalled = false
        private set
    var lastSavedBuyerProfile: BuyerProfile? = null
        private set

    /**
     * Set the seller profile to be returned
     */
    fun setSellerProfile(profile: SellerProfile) {
        _sellerProfile.value = profile
    }

    /**
     * Set the buyer profile to be returned
     */
    fun setBuyerProfile(profile: BuyerProfile?) {
        _buyerProfile.value = profile
    }

    /**
     * Reset repository state for fresh test
     */
    fun reset() {
        _buyerProfile.value = null
        _sellerProfile.value = null
        saveSellerProfileCalled = false
        lastSavedSellerProfile = null
        saveBuyerProfileCalled = false
        lastSavedBuyerProfile = null
        shouldFailGetSellerProfile = false
        shouldFailSaveSellerProfile = false
        shouldFailGetBuyerProfile = false
        shouldFailSaveBuyerProfile = false
        failureMessage = "Test error"
        _accessRequests.value = emptyList()
        _buyers.value = emptyMap()
        redeemedTokens.clear()
        currentBuyerId = "test-buyer-uid"
    }

    override fun observeBuyerProfile(): Flow<BuyerProfile?> {
        return _buyerProfile.asStateFlow()
    }

    override suspend fun getBuyerProfile(): Result<BuyerProfile> {
        if (shouldFailGetBuyerProfile) {
            return Result.failure(Exception(failureMessage))
        }

        val profile = _buyerProfile.value
        return if (profile != null) {
            Result.success(profile)
        } else {
            Result.failure(Exception("Buyer profile not found"))
        }
    }

    override suspend fun saveBuyerProfile(profile: BuyerProfile): Result<BuyerProfile> {
        saveBuyerProfileCalled = true
        lastSavedBuyerProfile = profile

        if (shouldFailSaveBuyerProfile) {
            return Result.failure(Exception(failureMessage))
        }

        _buyerProfile.value = profile
        return Result.success(profile)
    }

    override suspend fun getSellerDisplayName(sellerId: String): Result<String> =
        Result.success(_sellerProfile.value?.displayName ?: "")

    override suspend fun getSellerProfile(sellerId: String): Result<SellerProfile> {
        if (shouldFailGetSellerProfile) {
            return Result.failure(Exception(failureMessage))
        }

        val profile = _sellerProfile.value
        return if (profile != null) {
            Result.success(profile)
        } else {
            Result.failure(Exception("Seller profile not found"))
        }
    }

    override suspend fun saveSellerProfile(profile: SellerProfile): Result<Unit> {
        saveSellerProfileCalled = true
        lastSavedSellerProfile = profile

        if (shouldFailSaveSellerProfile) {
            return Result.failure(Exception(failureMessage))
        }

        _sellerProfile.value = profile
        return Result.success(Unit)
    }

    override suspend fun clearUserData(
        sellerId: String,
        buyerProfile: BuyerProfile
    ): Result<CleanUpResult> {
        _buyerProfile.value = null
        return Result.success(
            CleanUpResult(
                started = true,
                profileDeleted = true
            )
        )
    }

    override suspend fun deleteBuyerProfile(userId: String): Result<Unit> {
        _buyerProfile.value = null
        return Result.success(Unit)
    }
    override suspend fun saveDraftBasket(draftBasket: DraftBasket): Result<Unit> {
        val currentProfile = _buyerProfile.value
        if (currentProfile != null) {
            _buyerProfile.value = currentProfile.copy(draftBasket = draftBasket)
        }
        return Result.success(Unit)
    }

    override suspend fun clearDraftBasket(): Result<Unit> {
        val currentProfile = _buyerProfile.value
        if (currentProfile != null) {
            _buyerProfile.value = currentProfile.copy(draftBasket = null)
        }
        return Result.success(Unit)
    }

    // --- access records ---------------------------------------------------
    // One record per buyer, keyed by their auth uid, mirroring
    // buyer_access_status/{sellerId}/{buyerId}. Replaces the knownClients /
    // approvedBuyers / blockedClients maps the seller profile used to carry.

    private val _accessRequests = MutableStateFlow<List<AccessRequest>>(emptyList())
    private val _buyers = MutableStateFlow<Map<String, Map<String, BuyerAccess>>>(emptyMap())
    private val redeemedTokens = mutableMapOf<String, String>()

    /**
     * The uid the fake answers buyer-side calls as. The real repository reads it
     * from the auth session; tests that care set it here.
     */
    var currentBuyerId: String = "test-buyer-uid"

    /** Seed an access record, as a seller or a redemption would have written it. */
    fun setAccessStatus(
        sellerId: String,
        buyerId: String,
        status: AccessStatus,
        displayName: String = ""
    ) {
        putAccess(sellerId, BuyerAccess(buyerId, displayName, status, updatedAt = 1L))
    }

    /** Seed a pending request, as a buyer would have submitted it. */
    fun setAccessRequests(requests: List<AccessRequest>) {
        _accessRequests.value = requests
    }

    private fun putAccess(sellerId: String, access: BuyerAccess) {
        _buyers.update { all ->
            all + (sellerId to (all[sellerId].orEmpty() + (access.buyerId to access)))
        }
    }

    private fun accessOf(sellerId: String, buyerId: String): BuyerAccess? =
        _buyers.value[sellerId]?.get(buyerId)

    private fun clearRequest(buyerId: String) {
        _accessRequests.update { requests -> requests.filter { it.buyerId != buyerId } }
    }

    // --- buyer side -------------------------------------------------------

    override suspend fun submitAccessRequest(sellerId: String, displayName: String): Result<Unit> {
        putAccess(sellerId, BuyerAccess(currentBuyerId, displayName, AccessStatus.PENDING, 1L))
        _accessRequests.update { requests ->
            requests + AccessRequest(sellerId, currentBuyerId, displayName, requestedAt = 1L)
        }
        return Result.success(Unit)
    }

    override suspend fun cancelAccessRequest(sellerId: String): Result<Unit> {
        _buyers.update { all ->
            all + (sellerId to (all[sellerId].orEmpty() - currentBuyerId))
        }
        clearRequest(currentBuyerId)
        return Result.success(Unit)
    }

    override suspend fun redeemInviteToken(
        sellerId: String,
        token: String,
        displayName: String
    ): Result<Unit> {
        val alreadyRedeemedBy = redeemedTokens[token]
        if (alreadyRedeemedBy != null) {
            return Result.failure(IllegalStateException("Token already redeemed by $alreadyRedeemedBy"))
        }
        redeemedTokens[token] = currentBuyerId
        putAccess(sellerId, BuyerAccess(currentBuyerId, displayName, AccessStatus.APPROVED, 1L))
        return Result.success(Unit)
    }

    override suspend fun getAccessStatus(sellerId: String): AccessStatus =
        accessOf(sellerId, currentBuyerId)?.status ?: AccessStatus.NONE

    override fun observeAccessStatus(sellerId: String): Flow<AccessStatus> =
        _buyers.map { all -> all[sellerId]?.get(currentBuyerId)?.status ?: AccessStatus.NONE }

    override suspend fun updateOwnDisplayName(sellerId: String, displayName: String): Result<Unit> {
        val existing = accessOf(sellerId, currentBuyerId) ?: return Result.success(Unit)
        putAccess(sellerId, existing.copy(displayName = displayName))
        return Result.success(Unit)
    }

    // --- seller side ------------------------------------------------------

    override fun observeAccessRequests(sellerId: String): Flow<List<AccessRequest>> =
        _accessRequests.asStateFlow()

    override fun observeBuyers(sellerId: String): Flow<List<BuyerAccess>> =
        _buyers.map { all -> all[sellerId]?.values?.toList() ?: emptyList() }

    override suspend fun approveAccessRequest(
        sellerId: String,
        buyerId: String,
        displayName: String
    ): Result<Unit> {
        putAccess(sellerId, BuyerAccess(buyerId, displayName, AccessStatus.APPROVED, 1L))
        clearRequest(buyerId)
        return Result.success(Unit)
    }

    override suspend fun blockBuyer(sellerId: String, buyerId: String): Result<Unit> {
        val existing = accessOf(sellerId, buyerId)
        putAccess(
            sellerId,
            BuyerAccess(buyerId, existing?.displayName ?: "", AccessStatus.BLOCKED, 1L)
        )
        clearRequest(buyerId)
        return Result.success(Unit)
    }

    override suspend fun unblockBuyer(sellerId: String, buyerId: String): Result<Unit> {
        val existing = accessOf(sellerId, buyerId)
        putAccess(
            sellerId,
            BuyerAccess(buyerId, existing?.displayName ?: "", AccessStatus.APPROVED, 1L)
        )
        return Result.success(Unit)
    }

    override suspend fun createInviteToken(
        sellerId: String,
        displayNameHint: String,
        ttlMillis: Long,
        token: String?
    ): Result<String> = Result.success(token ?: "fake-invite-token")
}
